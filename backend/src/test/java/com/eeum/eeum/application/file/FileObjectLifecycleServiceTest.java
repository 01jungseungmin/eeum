package com.eeum.eeum.application.file;

import com.eeum.eeum.domain.file.entity.FileObject;
import com.eeum.eeum.domain.file.repository.FileObjectRepository;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
        given(fileObjectRepository.findByObjectKeyForUpdate("used/42/file.webp"))
                .willReturn(Optional.of(fileObject));
        service.attach(42L, FileUploadPurpose.USED, "used/42/file.webp");

        // When / Then
        assertThatThrownBy(() -> service.attach(42L, FileUploadPurpose.USED, "used/42/file.webp"))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_ALREADY_ATTACHED);
    }
}
