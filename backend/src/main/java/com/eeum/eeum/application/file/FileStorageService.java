package com.eeum.eeum.application.file;

import com.eeum.eeum.application.file.dto.request.FilePresignedUrlRequestDto;
import com.eeum.eeum.application.file.dto.request.FileUploadConfirmRequestDto;
import com.eeum.eeum.application.file.dto.response.FilePresignedGetUrlResponseDto;
import com.eeum.eeum.application.file.dto.response.FilePresignedUrlResponseDto;
import com.eeum.eeum.application.file.dto.response.FileUploadConfirmResponseDto;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.common.lock.RateLimitKeys;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.infrastructure.storage.S3StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_UPLOAD_URLS_PER_MINUTE = 20;
    private static final Duration UPLOAD_URL_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RateLimitService rateLimitService;
    private final FileObjectLifecycleService fileObjectLifecycleService;

    // S3로 직접 업로드하므로 이 메서드는 트랜잭션을 열지 않는다.
    public FilePresignedUrlResponseDto createPresignedUploadUrl(
            Long accountId, FilePresignedUrlRequestDto request) {
        assertConfigured();
        rateLimitService.checkAndIncrement(
                RateLimitKeys.fileUpload(accountId),
                MAX_UPLOAD_URLS_PER_MINUTE,
                UPLOAD_URL_RATE_LIMIT_WINDOW,
                ErrorCode.FILE_RATE_LIMITED
        );

        String contentType = normalizeAndValidateContentType(request.contentType());
        validateSize(request.contentLength());

        String objectKey = request.purpose().createTemporaryObjectKey(
                accountId,
                EXTENSIONS_BY_CONTENT_TYPE.get(contentType),
                UUID.randomUUID().toString()
        );

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                // 이 값은 서명에 포함된다. 프론트는 반드시 같은 Content-Type으로 PUT해야 한다.
                .contentType(contentType)
                .build();

        try {
            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(properties.uploadUrlExpiration())
                            .putObjectRequest(putObjectRequest)
                            .build()
            );

            return new FilePresignedUrlResponseDto(
                    objectKey,
                    presignedRequest.url().toString(),
                    Map.of("Content-Type", contentType),
                    presignedRequest.expiration()
            );
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 업로드 URL 발급에 실패했습니다", exception);
        }
    }

    // 프론트가 S3 PUT 직후 호출한다. 임시 객체를 검사한 뒤 최종 key로 복사한다.
    // 최종 key에는 Presigned PUT URL을 발급하지 않으므로, 이후 객체를 덮어쓸 수 없다.
    public FileUploadConfirmResponseDto confirmUpload(Long accountId, FileUploadConfirmRequestDto request) {
        assertConfigured();
        String temporaryObjectKey = requireOwnedTemporaryObjectKey(accountId, request.objectKey());
        FileUploadPurpose purpose = FileUploadPurpose.findTemporaryPurpose(temporaryObjectKey, accountId);
        java.util.Optional<String> existingObjectKey = fileObjectLifecycleService.findReusableConfirmedObjectKey(
                accountId, purpose, temporaryObjectKey);
        if (existingObjectKey.isPresent()) {
            return new FileUploadConfirmResponseDto(existingObjectKey.get());
        }

        try {
            HeadObjectResponse object = s3Client.headObject(builder -> builder
                    .bucket(properties.bucket())
                    .key(temporaryObjectKey));

            String contentType = normalizeAndValidateContentType(object.contentType());
            validateSize(object.contentLength());
            validateImageSignature(temporaryObjectKey, contentType);

            String objectKey = purpose.createObjectKey(
                    accountId,
                    EXTENSIONS_BY_CONTENT_TYPE.get(contentType),
                    UUID.randomUUID().toString()
            );
            s3Client.copyObject(CopyObjectRequest.builder()
                    .copySource(properties.bucket() + "/" + temporaryObjectKey)
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .metadataDirective("COPY")
                    .build());
            try {
                fileObjectLifecycleService.registerConfirmed(accountId, purpose, temporaryObjectKey, objectKey);
            } catch (DataIntegrityViolationException exception) {
                deleteObjectQuietly(objectKey);
                return fileObjectLifecycleService.findReusableConfirmedObjectKey(
                                accountId, purpose, temporaryObjectKey)
                        .map(FileUploadConfirmResponseDto::new)
                        .orElseThrow(() -> exception);
            } catch (RuntimeException exception) {
                deleteObjectQuietly(objectKey);
                throw exception;
            }
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(temporaryObjectKey)
                        .build());
            } catch (SdkException exception) {
                // 최종 객체 복사는 이미 성공했다. 임시 객체는 S3 Lifecycle이 정리하므로
                // 삭제 실패 때문에 클라이언트가 최종 key를 받지 못하게 하지 않는다.
                log.warn("S3 임시 이미지 삭제 실패: key={}", temporaryObjectKey, exception);
            }

            return new FileUploadConfirmResponseDto(objectKey);
        } catch (BusinessException exception) {
            throw exception;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 업로드 확인에 실패했습니다", exception);
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 업로드 확인에 실패했습니다", exception);
        }
    }

    // 개인 업로드 미리보기용이다. 공개 게시글·채팅 이미지의 조회 권한은 각 도메인 Service가 판단한 뒤 이 메서드를 사용한다.
    public FilePresignedGetUrlResponseDto createOwnedPresignedGetUrl(Long accountId, String objectKey) {
        assertConfigured();
        String ownedObjectKey = requireOwnedObjectKey(accountId, objectKey);
        return createPresignedGetUrl(ownedObjectKey);
    }

    // 도메인 Service가 대상 리소스의 공개/참여자 권한을 확인한 후 호출한다.
    public String resolveImageUrl(String imageUrl) {
        if (!isFinalObjectKey(imageUrl)) {
            return imageUrl;
        }
        return createPresignedGetUrl(imageUrl).downloadUrl();
    }

    // DB 저장 Service가 호출하는 순수 참조 검증이다. S3 I/O는 confirm 단계에서만 수행한다.
    // 기존 외부 HTTPS URL은 프론트의 점진 전환 기간에만 허용한다. 새 업로드는 confirm 응답의 final key를 사용한다.
    public void requireAttachableObject(Long accountId, FileUploadPurpose purpose, String objectKey) {
        if (isLegacyHttpsUrl(objectKey)) {
            return;
        }
        if (objectKey == null || objectKey.isBlank()
                || objectKey.contains("..") || objectKey.contains("\\")
                || !purpose.owns(objectKey, accountId)) {
            throw new ForbiddenException(ErrorCode.FILE_ACCESS_DENIED);
        }
        fileObjectLifecycleService.attach(accountId, purpose, objectKey);
    }

    private boolean isLegacyHttpsUrl(String value) {
        return value != null && value.startsWith("https://");
    }

    public FilePresignedGetUrlResponseDto createPresignedGetUrl(String objectKey) {
        assertConfigured();
        if (objectKey == null || objectKey.isBlank()) {
            throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
        }

        try {
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(properties.downloadUrlExpiration())
                            .getObjectRequest(builder -> builder.bucket(properties.bucket()).key(objectKey))
                            .build()
            );
            return new FilePresignedGetUrlResponseDto(
                    presignedRequest.url().toString(),
                    presignedRequest.expiration()
            );
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 조회 URL 발급에 실패했습니다", exception);
        }
    }

    // S3 삭제는 스케줄러가 호출하므로 트랜잭션을 열지 않는다.
    public void deleteObject(String objectKey) {
        assertConfigured();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 이미지 삭제에 실패했습니다", exception);
        }
    }

    private void assertConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_NOT_CONFIGURED);
        }
    }

    private String requireOwnedObjectKey(Long accountId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()
                || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
        }

        boolean owned = java.util.Arrays.stream(FileUploadPurpose.values())
                .anyMatch(purpose -> purpose.owns(objectKey, accountId) || purpose.ownsTemporary(objectKey, accountId));
        if (!owned) {
            throw new ForbiddenException(ErrorCode.FILE_ACCESS_DENIED);
        }
        return objectKey;
    }

    private String requireOwnedTemporaryObjectKey(Long accountId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()
                || objectKey.contains("..") || objectKey.contains("\\")
                || FileUploadPurpose.findTemporaryPurpose(objectKey, accountId) == null) {
            throw new ForbiddenException(ErrorCode.FILE_ACCESS_DENIED);
        }
        return objectKey;
    }

    public boolean isFinalObjectKey(String objectKey) {
        return objectKey != null && FileUploadPurpose.isFinalObjectKey(objectKey);
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (SdkException cleanupException) {
            log.error("S3 파일 메타데이터 저장 실패 후 객체 정리 실패: key={}", objectKey, cleanupException);
        }
    }

    private void validateImageSignature(String objectKey, String contentType) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .range("bytes=0-15")
                            .build(),
                    ResponseTransformer.toBytes());
            byte[] header = bytes.asByteArray();
            if (!matchesImageSignature(header, contentType)) {
                throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 이미지 형식 확인에 실패했습니다", exception);
        }
    }

    private boolean matchesImageSignature(byte[] header, String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }
        if ("image/png".equals(contentType)) {
            return header.length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                    && header[4] == 0x0D && header[5] == 0x0A
                    && header[6] == 0x1A && header[7] == 0x0A;
        }
        return header.length >= 12
                && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
    }

    private String normalizeAndValidateContentType(String contentType) {
        if (contentType == null) {
            throw new BadRequestException(ErrorCode.FILE_UNSUPPORTED_CONTENT_TYPE);
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS_BY_CONTENT_TYPE.containsKey(normalized)) {
            throw new BadRequestException(ErrorCode.FILE_UNSUPPORTED_CONTENT_TYPE);
        }
        return normalized;
    }

    private void validateSize(Long contentLength) {
        if (contentLength == null || contentLength <= 0) {
            throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
        }
        if (contentLength > MAX_IMAGE_SIZE_BYTES) {
            throw new BadRequestException(ErrorCode.FILE_TOO_LARGE);
        }
    }
}
