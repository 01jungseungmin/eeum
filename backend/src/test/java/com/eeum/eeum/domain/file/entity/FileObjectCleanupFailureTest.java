package com.eeum.eeum.domain.file.entity;

import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 증상: 정리에 계속 실패하는 행이 CLEANUP_PENDING에 남아 매 회차 다시 집힌다.
 * 결함 위치: FileObject의 정리 상태 전이.
 * 한계까지 실패하면 CLEANUP_FAILED로 내려 스케줄러 대상에서 빠지고, 첨부도 계속 막는다.
 */
class FileObjectCleanupFailureTest {

    private static final int MAX_ATTEMPTS = 3;

    @Test
    void 한계_전까지는_정리_대상을_유지한다() {
        // Given
        FileObject fileObject = cleanupPending();

        // When
        boolean givenUp = fileObject.recordCleanupFailure(MAX_ATTEMPTS);

        // Then
        assertThat(givenUp).isFalse();
        assertThat(fileObject.getStatus()).isEqualTo(FileObjectStatus.CLEANUP_PENDING);
        assertThat(fileObject.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void 한계를_넘기면_정리를_포기해_스케줄러_대상에서_빠진다() {
        // Given
        FileObject fileObject = cleanupPending();

        // When
        boolean givenUp = false;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            givenUp = fileObject.recordCleanupFailure(MAX_ATTEMPTS);
        }

        // Then
        assertThat(givenUp).isTrue();
        assertThat(fileObject.getStatus()).isEqualTo(FileObjectStatus.CLEANUP_FAILED);
        // claimExpiredUnattached는 CLEANUP_PENDING과 CONFIRMED만 집으므로 더 이상 재시도되지 않는다.
        assertThat(fileObject.claimCleanup()).isFalse();
    }

    @Test
    void 정리를_포기한_객체는_다시_첨부할_수_없다() {
        // Given — S3 객체가 이미 지워졌을 수 있어 되살리면 깨진 이미지를 참조하게 된다.
        FileObject fileObject = cleanupPending();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fileObject.recordCleanupFailure(MAX_ATTEMPTS);
        }

        // When / Then
        assertThatThrownBy(fileObject::attach)
                .isInstanceOf(IllegalStateException.class);
    }

    private FileObject cleanupPending() {
        FileObject fileObject = FileObject.confirmed(
                42L, "USED", "tmp/used/42/file.webp", "used/42/file.webp");
        ReflectionTestUtils.setField(fileObject, "status", FileObjectStatus.CLEANUP_PENDING);
        return fileObject;
    }
}
