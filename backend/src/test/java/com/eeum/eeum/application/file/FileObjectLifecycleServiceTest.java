package com.eeum.eeum.application.file;

import com.eeum.eeum.domain.file.entity.FileObject;
import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import com.eeum.eeum.domain.file.repository.FileObjectRepository;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
     * 한 번의 잠금 조회로 묶고, PK 오름차순으로 읽어 프로젝트 잠금 순서 규약을 따른다.
     */
    @Test
    void 여러_key는_한_번의_잠금_조회로_정리_대상이_된다() {
        // Given
        FileObject first = attached("used/42/a.webp");
        FileObject second = attached("used/42/b.webp");
        FileObjectLifecycleService service = new FileObjectLifecycleService(fileObjectRepository);
        List<String> keys = List.of("used/42/a.webp", "used/42/b.webp");
        given(fileObjectRepository.findForUpdateByObjectKeyInOrderByFileObjectIdAsc(keys))
                .willReturn(List.of(first, second));

        // When
        service.detachAll(keys);

        // Then
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
        verify(fileObjectRepository, never())
                .findForUpdateByObjectKeyInOrderByFileObjectIdAsc(org.mockito.ArgumentMatchers.any());
    }

    private FileObject attached(String objectKey) {
        FileObject fileObject = FileObject.confirmed(
                42L, FileUploadPurpose.USED.name(), "tmp/" + objectKey, objectKey);
        fileObject.attach();
        return fileObject;
    }
}
