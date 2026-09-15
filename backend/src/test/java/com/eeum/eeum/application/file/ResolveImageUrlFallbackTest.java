package com.eeum.eeum.application.file;

import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.infrastructure.storage.S3StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증상: 저장소 미설정 환경에서 이미지가 들어간 모든 조회 응답이 503으로 죽었다.
 * 결함 위치: FileStorageService.resolveImageUrl이 createPresignedGetUrl의 assertConfigured를 그대로 탔다.
 * 조회 URL 변환이 Advice에서 Service로 옮겨오면서 이 경로가 모든 응답에 깔렸다 —
 * 설정 공백으로 읽기 경로를 막지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ResolveImageUrlFallbackTest {

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private AwsCredentialsProvider awsCredentialsProvider;
    @Mock private RateLimitService rateLimitService;
    @Mock private FileObjectLifecycleService fileObjectLifecycleService;

    @Test
    void 저장소가_설정되지_않으면_objectKey를_그대로_돌려준다() {
        // Given — 로컬·테스트 환경처럼 버킷이 비어 있다.
        FileStorageService service = serviceWithBucket("");

        // When
        String resolved = service.resolveImageUrl("used/42/a.webp");

        // Then — 예외 없이 지나간다. 서명할 수단이 없을 뿐 조회를 막을 이유는 없다.
        assertThat(resolved).isEqualTo("used/42/a.webp");
    }

    @Test
    void objectKey가_아니면_설정과_무관하게_그대로_돌려준다() {
        // Given
        FileStorageService service = serviceWithBucket("eeum-prod-media-2026");

        // When / Then — 전환 기간의 외부 URL과 null이 여기서 걸러진다.
        assertThat(service.resolveImageUrl("https://cdn.example/legacy.png"))
                .isEqualTo("https://cdn.example/legacy.png");
        assertThat(service.resolveImageUrl(null)).isNull();
    }

    private FileStorageService serviceWithBucket(String bucket) {
        return new FileStorageService(
                new S3StorageProperties(bucket, "ap-northeast-2",
                        Duration.ofMinutes(10), Duration.ofMinutes(10), true),
                s3Client,
                s3Presigner,
                awsCredentialsProvider,
                rateLimitService,
                fileObjectLifecycleService
        );
    }
}
