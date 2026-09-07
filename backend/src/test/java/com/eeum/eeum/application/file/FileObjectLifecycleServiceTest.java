package com.eeum.eeum.application.file;

import com.eeum.eeum.domain.file.entity.FileObject;
import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import com.eeum.eeum.domain.file.repository.FileObjectRepository;
import com.eeum.eeum.domain.file.repository.FileObjectIdProjection;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 증상: 같은 final key를 두 요청이 연결하면 낙관적 락 충돌로 한 요청이 500이 됐다.
 * 결함 위치: FileObjectLifecycleService.attach.
 * 비관적 잠금 조회 뒤 이미 연결된 객체를 명시적 충돌로 처리해, 파일의 다중 참조를 막는 동작을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class FileObjectLifecycleServiceTest {

    @Mock
    private FileObjectRepository fileObjectRepository;

    @Test
    void 이미_연결된_파일은_명시적_충돌로_거부한다() {
        // Given
        FileObject fileObject = FileObject.confirmed(
                42L, FileUploadPurpose.USED.name(), "tmp/used/42/file.webp", "used/42/file.webp");
        FileObjectLifecycleService service = new FileObjectLifecycleService(fileObjectRepository);
        given(fileObjectRepository.findForUpdateByObjectKey("used/42/file.webp"))
                .willReturn(Optional.of(fileObject));
        service.attach(42L, FileUploadPurpose.USED, "used/42/file.webp");

        // When / Then
        assertThatThrownBy(() -> service.attach(42L, FileUploadPurpose.USED, "used/42/file.webp"))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_ALREADY_ATTACHED);
    }

    /**
     * 증상: 이미지 여러 장을 정리할 때 장수만큼 SELECT ... FOR UPDATE가 나가고,
     * 잠금 순서가 표시 순서에 끌려간다.
     * 결함 위치: 호출부가 key마다 detach를 부르던 방식.
     * 한 번의 잠금 조회로 묶는다. 잠금은 PK로 걸어야 InnoDB의 스캔 순서가 ID 오름차순이
     * 되므로, object_key로 고른 뒤 ID로 다시 잠근다.
     */
    @Test
    void 여러_key는_ID_오름차순_잠금_조회_한_번으로_정리_대상이_된다() {
        // Given — 표시 순서(a, b)와 ID 순서(2, 1)를 일부러 어긋나게 둔다.
        FileObject first = attached("used/42/a.webp", 2L);
        FileObject second = attached("used/42/b.webp", 1L);
        FileObjectLifecycleService service = new FileObjectLifecycleService(fileObjectRepository);
        List<String> keys = List.of("used/42/a.webp", "used/42/b.webp");
        given(fileObjectRepository.findByObjectKeyIn(keys)).willReturn(List.of(projection(2L), projection(1L)));
        given(fileObjectRepository.findForUpdateByFileObjectIdInOrderByFileObjectIdAsc(List.of(1L, 2L)))
                .willReturn(List.of(second, first));

        // When
        service.detachAll(keys);

        // Then — 잠금 조회에 넘긴 ID가 오름차순이어야 한다(위 stubbing이 그것을 고정한다).
        assertThat(first.getStatus()).isEqualTo(FileObjectStatus.CLEANUP_PENDING);
        assertThat(second.getStatus()).isEqualTo(FileObjectStatus.CLEANUP_PENDING);
    }

    @Test
    void 정리할_key가_없으면_조회하지_않는다() {
        // Given
        FileObjectLifecycleService service = new FileObjectLifecycleService(fileObjectRepository);

        // When
        service.detachAll(List.of());

        // Then
        verify(fileObjectRepository, never()).findByObjectKeyIn(any());
        verify(fileObjectRepository, never())
                .findForUpdateByFileObjectIdInOrderByFileObjectIdAsc(any());
    }

    @Test
    void 이미_사라진_key만_있으면_잠금_조회를_하지_않는다() {
        // Given — 다른 요청이 먼저 정리해 행이 남아 있지 않다.
        FileObjectLifecycleService service = new FileObjectLifecycleService(fileObjectRepository);
        List<String> keys = List.of("used/42/gone.webp");
        given(fileObjectRepository.findByObjectKeyIn(keys)).willReturn(List.of());

        // When
        service.detachAll(keys);

        // Then — 빈 IN 절로 잠금 조회를 날리지 않는다.
        verify(fileObjectRepository, never())
                .findForUpdateByFileObjectIdInOrderByFileObjectIdAsc(any());
    }

    private FileObject attached(String objectKey, Long fileObjectId) {
        FileObject fileObject = FileObject.confirmed(
                42L, FileUploadPurpose.USED.name(), "tmp/" + objectKey, objectKey);
        ReflectionTestUtils.setField(fileObject, "fileObjectId", fileObjectId);
        fileObject.attach();
        return fileObject;
    }

    @Test
    void 여러_key를_첨부할_때는_ID_오름차순으로_한번에_잠근다() {
        // Given — 클라이언트가 보낸 key 순서와 DB PK 순서를 일부러 반대로 둔다.
        FileObject first = confirmed("used/42/a.webp", 2L);
        FileObject second = confirmed("used/42/b.webp", 1L);
        FileObjectLifecycleService service = new FileObjectLifecycleService(fileObjectRepository);
        List<String> keys = List.of("used/42/a.webp", "used/42/b.webp");
        given(fileObjectRepository.findByObjectKeyIn(keys)).willReturn(List.of(projection(2L), projection(1L)));
        given(fileObjectRepository.findForUpdateByFileObjectIdInOrderByFileObjectIdAsc(List.of(1L, 2L)))
                .willReturn(List.of(second, first));

        // When
        service.attachAll(42L, FileUploadPurpose.USED, keys);

        // Then
        assertThat(first.getStatus()).isEqualTo(FileObjectStatus.ATTACHED);
        assertThat(second.getStatus()).isEqualTo(FileObjectStatus.ATTACHED);
        verify(fileObjectRepository)
                .findForUpdateByFileObjectIdInOrderByFileObjectIdAsc(List.of(1L, 2L));
    }

    private FileObject confirmed(String objectKey, Long fileObjectId) {
        FileObject fileObject = FileObject.confirmed(
                42L, FileUploadPurpose.USED.name(), "tmp/" + objectKey, objectKey);
        ReflectionTestUtils.setField(fileObject, "fileObjectId", fileObjectId);
        return fileObject;
    }

    private FileObjectIdProjection projection(Long fileObjectId) {
        return () -> fileObjectId;
    }
}
