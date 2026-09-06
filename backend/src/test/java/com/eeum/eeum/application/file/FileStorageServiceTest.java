package com.eeum.eeum.application.file;

import com.eeum.eeum.application.file.dto.request.FilePresignedUrlRequestDto;
import com.eeum.eeum.application.file.dto.response.FilePresignedUrlResponseDto;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.storage.S3StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(
                new S3StorageProperties("eeum-prod-media-2026", "ap-northeast-2",
                        Duration.ofMinutes(10), Duration.ofMinutes(10)),
                s3Client,
                s3Presigner
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
        assertThat(captor.getValue().putObjectRequest().key()).startsWith("used/42/").endsWith(".webp");
        assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/webp");
        assertThat(result.objectKey()).startsWith("used/42/").endsWith(".webp");
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
}
