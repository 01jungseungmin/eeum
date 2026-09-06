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
import com.eeum.eeum.infrastructure.storage.S3StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    // S3로 직접 업로드하므로 이 메서드는 트랜잭션을 열지 않는다.
    public FilePresignedUrlResponseDto createPresignedUploadUrl(
            Long accountId, FilePresignedUrlRequestDto request) {
        assertConfigured();

        String contentType = normalizeAndValidateContentType(request.contentType());
        validateSize(request.contentLength());

        String objectKey = request.purpose().createObjectKey(
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

    // 프론트가 S3 PUT 직후 호출한다. 실제 객체의 타입·크기를 다시 검사해 DB 저장 전에 검증한다.
    public FileUploadConfirmResponseDto confirmUpload(Long accountId, FileUploadConfirmRequestDto request) {
        assertConfigured();
        String objectKey = requireOwnedObjectKey(accountId, request.objectKey());

        try {
            HeadObjectResponse object = s3Client.headObject(builder -> builder
                    .bucket(properties.bucket())
                    .key(objectKey));

            normalizeAndValidateContentType(object.contentType());
            validateSize(object.contentLength());
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
                .anyMatch(purpose -> purpose.owns(objectKey, accountId));
        if (!owned) {
            throw new ForbiddenException(ErrorCode.FILE_ACCESS_DENIED);
        }
        return objectKey;
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
