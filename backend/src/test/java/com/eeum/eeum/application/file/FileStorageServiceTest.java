package com.eeum.eeum.application.file;

import com.eeum.eeum.application.file.dto.request.FilePresignedUrlRequestDto;
import com.eeum.eeum.application.file.dto.request.FileUploadConfirmRequestDto;
import com.eeum.eeum.application.file.dto.response.FileUploadConfirmResponseDto;
import com.eeum.eeum.application.file.dto.response.FilePresignedUrlResponseDto;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.storage.S3StorageProperties;
import com.eeum.eeum.common.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import software.amazon.awssdk.core.ResponseBytes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

/**
 * 증상: confirm 응답 유실 뒤 동일 tmp key를 재전송하면 새 final object가 생성됐다.
 * 결함 위치: FileStorageService.confirmUpload.
 * 이 테스트는 같은 tmp key가 항상 처음 확정한 objectKey를 반환함을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private FileObjectLifecycleService fileObjectLifecycleService;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    @Mock
    private HeadObjectResponse headObjectResponse;

    @Mock
    private ResponseBytes<GetObjectResponse> imageHeader;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(
                new S3StorageProperties("eeum-prod-media-2026", "ap-northeast-2",
                        Duration.ofMinutes(10), Duration.ofMinutes(10)),
                s3Client,
                s3Presigner,
                rateLimitService,
                fileObjectLifecycleService
        );
    }

    @Test
    void 허용된_이미지면_계정_소유_경로의_Presigned_PUT_URL을_발급한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedPutObjectRequest);
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://example.com/upload"));
        given(presignedPutObjectRequest.expiration()).willReturn(Instant.parse("2026-09-06T10:00:00Z"));

        FilePresignedUrlResponseDto result = fileStorageService.createPresignedUploadUrl(
                42L,
                new FilePresignedUrlRequestDto(FileUploadPurpose.USED, "image/webp", 1024L)
        );

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo("eeum-prod-media-2026");
        assertThat(captor.getValue().putObjectRequest().key()).startsWith("tmp/used/42/").endsWith(".webp");
        assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/webp");
        assertThat(result.objectKey()).startsWith("tmp/used/42/").endsWith(".webp");
        assertThat(result.headers()).containsEntry("Content-Type", "image/webp");
    }

    @Test
    void 지원하지_않는_이미지_타입은_URL을_발급하지_않는다() {
        assertThatThrownBy(() -> fileStorageService.createPresignedUploadUrl(
                42L,
                new FilePresignedUrlRequestDto(FileUploadPurpose.USED, "image/gif", 1024L)
        ))
                .isInstanceOf(BadRequestException.class)
                .extracting(exception -> ((BadRequestException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UNSUPPORTED_CONTENT_TYPE);
    }

    @Test
    void 최대_크기_10MB를_초과한_파일은_URL을_발급하지_않는다() {
        assertThatThrownBy(() -> fileStorageService.createPresignedUploadUrl(
                42L,
                new FilePresignedUrlRequestDto(FileUploadPurpose.USED, "image/png", 10L * 1024 * 1024 + 1)
        ))
                .isInstanceOf(BadRequestException.class)
                .extracting(exception -> ((BadRequestException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void 다른_계정이나_다른_용도의_최종_objectKey는_도메인에_연결할_수_없다() {
        assertThatThrownBy(() -> fileStorageService.requireAttachableObject(
                42L, FileUploadPurpose.USED, "products/42/file.webp"))
                .isInstanceOf(com.eeum.eeum.exception.ForbiddenException.class)
                .extracting(exception -> ((com.eeum.eeum.exception.ForbiddenException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_ACCESS_DENIED);
    }

    @Test
    void 같은_임시_objectKey를_다시_confirm하면_처음_확정한_결과를_반환한다() {
        // Given — confirm 응답이 유실되어 클라이언트가 동일한 tmp key를 재전송한다.
        String temporaryObjectKey = "tmp/used/42/upload.png";
        given(s3Client.headObject(any(java.util.function.Consumer.class))).willReturn(headObjectResponse);
        given(headObjectResponse.contentType()).willReturn("image/png");
        given(headObjectResponse.contentLength()).willReturn(100L);
        given(s3Client.getObject(
                any(GetObjectRequest.class),
                org.mockito.ArgumentMatchers.<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any()
        )).willReturn(imageHeader);
        given(imageHeader.asByteArray()).willReturn(new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        });
        AtomicReference<String> confirmedObjectKey = new AtomicReference<>();
        given(fileObjectLifecycleService.findReusableConfirmedObjectKey(
                42L, FileUploadPurpose.USED, temporaryObjectKey))
                .willAnswer(invocation -> Optional.ofNullable(confirmedObjectKey.get()));
        willAnswer(invocation -> {
            confirmedObjectKey.set(invocation.getArgument(3));
            return null;
        }).given(fileObjectLifecycleService).registerConfirmed(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(FileUploadPurpose.USED),
                org.mockito.ArgumentMatchers.eq(temporaryObjectKey),
                any(String.class));

        // When
        FileUploadConfirmResponseDto first = fileStorageService.confirmUpload(
                42L, new FileUploadConfirmRequestDto(temporaryObjectKey));
        FileUploadConfirmResponseDto retried = fileStorageService.confirmUpload(
                42L, new FileUploadConfirmRequestDto(temporaryObjectKey));

        // Then — 재시도는 새 final object를 만들지 않고 같은 key를 돌려준다.
        assertThat(retried.objectKey()).isEqualTo(first.objectKey());
    }
}
