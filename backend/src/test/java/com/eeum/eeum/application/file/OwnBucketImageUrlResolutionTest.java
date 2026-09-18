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
 * 증상: 상점 이미지 8개가 전부 403 AccessDenied로 깨졌다.
 * 결함 위치: DB에 objectKey가 아니라 완성된 S3 URL이 저장된 행은 isFinalObjectKey를 통과하지
 * 못해 서명을 타지 않았고, 브라우저가 비공개 객체를 직접 받으려다 실패했다.
 * 외부 CDN URL까지 같이 서명 대상이 되면 정상 이미지가 깨지므로 버킷 호스트 일치로 좁힌다.
 */
@ExtendWith(MockitoExtension.class)
class OwnBucketImageUrlResolutionTest {

    private static final String BUCKET = "eeum-prod-media-2026";
    private static final String REGION = "ap-northeast-2";

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private AwsCredentialsProvider awsCredentialsProvider;
    @Mock private RateLimitService rateLimitService;
    @Mock private FileObjectLifecycleService fileObjectLifecycleService;

    @Test
    void 우리_버킷을_가리키는_전체_URL은_서명_대상이다() {
        FileStorageService service = configuredService();

        assertThat(service.isResolvableImageRef(
                "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/demo/shop/main.png")).isTrue();
        assertThat(service.isResolvableImageRef(
                "https://" + BUCKET + ".s3.amazonaws.com/demo/shop/main.png")).isTrue();
        assertThat(service.isResolvableImageRef(
                "https://s3." + REGION + ".amazonaws.com/" + BUCKET + "/demo/shop/main.png")).isTrue();
    }

    @Test
    void 인코딩되지_않은_한글_경로도_서명_대상이다() {
        // URI 파싱은 한글에서 예외가 난다 — 문자열 접두사 비교로 처리해야 이 행이 살아난다.
        FileStorageService service = configuredService();

        assertThat(service.isResolvableImageRef(
                "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/demo/온누리커피/main.png")).isTrue();
    }

    @Test
    void 외부_CDN_URL은_건드리지_않는다() {
        // 중고거래 썸네일이 여기 해당한다. 서명을 시도하면 정상 이미지가 깨진다.
        FileStorageService service = configuredService();

        assertThat(service.isResolvableImageRef("https://picsum.photos/seed/1/600/600")).isFalse();
        assertThat(service.isResolvableImageRef("https://cdn.example/legacy.png")).isFalse();
        assertThat(service.isResolvableImageRef("https://other-bucket.s3." + REGION + ".amazonaws.com/a.png"))
                .isFalse();
    }

    @Test
    void 이미_서명된_URL은_다시_서명하지_않는다() {
        FileStorageService service = configuredService();

        assertThat(service.isResolvableImageRef("https://" + BUCKET + ".s3." + REGION
                + ".amazonaws.com/demo/a.png?X-Amz-Signature=abc&X-Amz-Expires=600")).isFalse();
    }

    @Test
    void 기존_objectKey_판정은_그대로_유지된다() {
        FileStorageService service = configuredService();

        assertThat(service.isResolvableImageRef("used/42/a.webp")).isTrue();
        assertThat(service.isResolvableImageRef("stores/7/cover.png")).isTrue();
        assertThat(service.isResolvableImageRef(null)).isFalse();
        assertThat(service.isResolvableImageRef("")).isFalse();
    }

    @Test
    void 저장소_미설정이면_버킷_URL도_서명_대상이_아니다() {
        FileStorageService service = serviceWithBucket("");

        assertThat(service.isResolvableImageRef(
                "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/demo/a.png")).isFalse();
    }

    private FileStorageService configuredService() {
        return serviceWithBucket(BUCKET);
    }

    private FileStorageService serviceWithBucket(String bucket) {
        return new FileStorageService(
                new S3StorageProperties(bucket, REGION,
                        Duration.ofMinutes(10), Duration.ofMinutes(10), true),
                s3Client,
                s3Presigner,
                awsCredentialsProvider,
                rateLimitService,
                fileObjectLifecycleService
        );
    }
}
