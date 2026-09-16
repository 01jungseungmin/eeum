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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import software.amazon.awssdk.core.ResponseBytes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    private AwsCredentialsProvider awsCredentialsProvider;

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
                        Duration.ofMinutes(10), Duration.ofMinutes(10), true),
                s3Client,
                s3Presigner,
                awsCredentialsProvider,
                rateLimitService,
                fileObjectLifecycleService
        );
        lenient().when(awsCredentialsProvider.resolveCredentials())
                .thenReturn(AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    }

    @Test
    void 허용된_이미지면_계정_소유_경로의_Presigned_PUT_URL을_발급한다() throws Exception {
        FilePresignedUrlResponseDto result = fileStorageService.createPresignedUploadUrl(
                42L,
                new FilePresignedUrlRequestDto(FileUploadPurpose.USED, "image/webp", 1024L)
        );

        assertThat(result.objectKey()).startsWith("tmp/used/42/").endsWith(".webp");
        assertThat(result.uploadMethod()).isEqualTo("POST");
        assertThat(result.formFields())
                .containsEntry("key", result.objectKey())
                .containsEntry("Content-Type", "image/webp")
                .containsKey("policy");
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
    void 여러_final_key는_생명주기_서비스에_한번에_첨부를_요청한다() {
        // Given
        List<String> objectKeys = List.of("used/42/a.webp", "used/42/b.webp");

        // When
        fileStorageService.requireAttachableObjects(42L, FileUploadPurpose.USED, objectKeys);

        // Then
        verify(fileObjectLifecycleService).attachAll(42L, FileUploadPurpose.USED, objectKeys);
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
        given(s3Client.headObject(any(Consumer.class))).willReturn(headObjectResponse);
        given(headObjectResponse.contentType()).willReturn("image/png");
        given(headObjectResponse.contentLength()).willReturn(100L);
        given(headObjectResponse.eTag()).willReturn("\"original\"");
        given(s3Client.getObject(
                any(GetObjectRequest.class),
                ArgumentMatchers.<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any()
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
        // 검사와 복사가 동일한 객체를 대상으로 해야 임시 파일 덮어쓰기를 차단한다.
        ArgumentCaptor<GetObjectRequest> getRequest = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(getRequest.capture(), any(ResponseTransformer.class));
        assertThat(getRequest.getValue().ifMatch()).isEqualTo("\"original\"");
        ArgumentCaptor<CopyObjectRequest> copyRequest =
                ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copyRequest.capture());
        assertThat(copyRequest.getValue().copySourceIfMatch()).isEqualTo("\"original\"");
        // COPY면 S3가 원본 메타데이터를 복사해 정규화한 Content-Type이 최종 객체에서 사라진다.
        assertThat(copyRequest.getValue().metadataDirectiveAsString()).isEqualTo("REPLACE");
        assertThat(copyRequest.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void 다른_confirm이_임시_객체를_삭제한_뒤에도_기존_확정_결과를_반환한다() {
        // Given — 첫 확인 조회 직후 다른 요청이 확정 등록과 tmp 삭제를 완료했다.
        // 기존 구현은 headObject 404를 FILE_NOT_FOUND로 바꿔 정상 재시도를 실패시켰다.
        String temporaryObjectKey = "tmp/used/42/upload.png";
        String confirmedObjectKey = "used/42/confirmed.png";
        given(fileObjectLifecycleService.findReusableConfirmedObjectKey(
                42L, FileUploadPurpose.USED, temporaryObjectKey))
                .willReturn(Optional.empty(), Optional.of(confirmedObjectKey));
        given(s3Client.headObject(any(Consumer.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        // When
        FileUploadConfirmResponseDto result = fileStorageService.confirmUpload(
                42L, new FileUploadConfirmRequestDto(temporaryObjectKey));

        // Then — 경쟁에서 진 요청도 이미 확정된 결과를 멱등하게 받는다.
        assertThat(result.objectKey()).isEqualTo(confirmedObjectKey);
    }

    @Test
    void 업로드_정책에는_실제_본문_크기_상한이_포함된다() throws Exception {
        // Given
        // When
        FilePresignedUrlResponseDto result = fileStorageService.createPresignedUploadUrl(
                42L, new FilePresignedUrlRequestDto(FileUploadPurpose.USED, "image/png", 1024L));

        // Then — 클라이언트 선언값이 아니라 S3가 강제하는 정책 필드가 반환돼야 한다.
        String policy = new String(Base64.getDecoder().decode(result.formFields().get("policy")));
        assertThat(policy).contains("[\"content-length-range\",1,10485760]");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 검사나_복사_중_객체가_바뀌면_확정하지_않는다(boolean changedDuringCopy) {
        // Given — S3의 If-Match 실패를 재현하며 DB 등록이 일어나지 않아야 한다.
        String key = "tmp/used/42/upload.png";
        given(s3Client.headObject(any(Consumer.class))).willReturn(headObjectResponse);
        given(headObjectResponse.contentType()).willReturn("image/png");
        given(headObjectResponse.contentLength()).willReturn(100L);
        given(headObjectResponse.eTag()).willReturn("\"original\"");
        S3Exception changed = (S3Exception) S3Exception.builder().statusCode(412).build();
        if (changedDuringCopy) {
            given(s3Client.getObject(any(GetObjectRequest.class),
                    ArgumentMatchers.<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any()))
                    .willReturn(imageHeader);
            given(imageHeader.asByteArray()).willReturn(new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            given(s3Client.copyObject(any(CopyObjectRequest.class)))
                    .willThrow(changed);
        } else {
            given(s3Client.getObject(any(GetObjectRequest.class),
                    ArgumentMatchers.<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any()))
                    .willThrow(changed);
        }
        // When / Then
        assertThatThrownBy(() -> fileStorageService.confirmUpload(42L, new FileUploadConfirmRequestDto(key)))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_UPLOAD_INVALID);
        verify(fileObjectLifecycleService).findReusableConfirmedObjectKey(42L, FileUploadPurpose.USED, key);
        verifyNoMoreInteractions(fileObjectLifecycleService);
    }

    @Test
    void 조회용_서명_URL은_영구_이미지로_첨부하지_않는다() {
        // Given — 응답 URL 재저장은 기존 프로필을 detach하고 만료 URL을 저장하게 한다.
        String url = "https://eeum-prod-media-2026.s3.ap-northeast-2.amazonaws.com/profiles/42/a.png?X-Amz-Signature=abc";
        // When / Then
        assertThatThrownBy(() -> fileStorageService.requireAttachableObject(42L, FileUploadPurpose.PROFILE, url))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_UPLOAD_INVALID);
    }
}
