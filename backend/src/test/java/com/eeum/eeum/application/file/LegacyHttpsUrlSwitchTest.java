package com.eeum.eeum.application.file;

import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.infrastructure.storage.S3StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 증상: 인증된 사용자가 임의 외부 HTTPS URL을 이미지 필드에 저장할 수 있다.
 * 결함 위치: FileStorageService.requireAttachableObject의 legacy URL 예외 경로.
 * 전환 기간 예외라 없앨 수는 없지만, 끄고 켤 수단과 종료 조건이 있어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class LegacyHttpsUrlSwitchTest {

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private AwsCredentialsProvider awsCredentialsProvider;
    @Mock private RateLimitService rateLimitService;
    @Mock private FileObjectLifecycleService fileObjectLifecycleService;

    @Test
    void 스위치를_내리면_외부_URL은_저장할_수_없다() {
        // Given — 전환 완료 후 상태.
        FileStorageService service = service(false);

        // When / Then — 소유권 모델이 없는 값이 이미지 필드에 들어오지 못한다.
        assertThatThrownBy(() -> service.requireAttachableObject(
                42L, FileUploadPurpose.USED, "https://attacker.example/px.gif"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_ACCESS_DENIED);
    }

    @Test
    void 스위치가_켜져_있으면_전환_기간_URL을_받는다() {
        // Given — 프론트 전환 중.
        FileStorageService service = service(true);

        // When / Then
        assertThatCode(() -> service.requireAttachableObject(
                42L, FileUploadPurpose.USED, "https://cdn.example/legacy.png"))
                .doesNotThrowAnyException();
    }

    private FileStorageService service(boolean allowLegacyHttpsUrl) {
        return new FileStorageService(
                new S3StorageProperties("eeum-prod-media-2026", "ap-northeast-2",
                        Duration.ofMinutes(10), Duration.ofMinutes(10), allowLegacyHttpsUrl),
                s3Client,
                s3Presigner,
                awsCredentialsProvider,
                rateLimitService,
                fileObjectLifecycleService
        );
    }
}
