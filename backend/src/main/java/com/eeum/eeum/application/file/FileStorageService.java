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
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import java.time.Instant;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    // file_object.object_key 컬럼 길이와 같은 값이어야 한다. 어긋나면 검증을 통과한 key가
    // 저장 시점에 잘려 나간다.
    private static final int MAX_OBJECT_KEY_LENGTH = 500;
    private static final int MAX_UPLOAD_URLS_PER_MINUTE = 20;
    private static final Duration UPLOAD_URL_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final DateTimeFormatter AWS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AWS_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final S3StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsCredentialsProvider awsCredentialsProvider;
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

        try {
            Instant now = Instant.now();
            Map<String, String> formFields = createPresignedPostFields(objectKey, contentType, now);

            return new FilePresignedUrlResponseDto(
                    objectKey,
                    "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/",
                    formFields,
                    "POST",
                    now.plus(properties.uploadUrlExpiration())
            );
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 업로드 URL 발급에 실패했습니다", exception);
        }
    }

    private Map<String, String> createPresignedPostFields(String objectKey, String contentType, Instant now) {
        AwsCredentials credentials = awsCredentialsProvider.resolveCredentials();
        String date = AWS_DATE.format(now);
        String timestamp = AWS_TIMESTAMP.format(now);
        String credential = credentials.accessKeyId() + "/" + date + "/" + properties.region() + "/s3/aws4_request";
        Instant expiration = now.plus(properties.uploadUrlExpiration());

        StringBuilder conditions = new StringBuilder()
                .append("[{\"bucket\":\"").append(properties.bucket()).append("\"}")
                .append(",{\"key\":\"").append(objectKey).append("\"}")
                .append(",{\"Content-Type\":\"").append(contentType).append("\"}")
                .append(",[\"content-length-range\",1,").append(MAX_IMAGE_SIZE_BYTES).append("]")
                .append(",{\"x-amz-algorithm\":\"AWS4-HMAC-SHA256\"}")
                .append(",{\"x-amz-credential\":\"").append(credential).append("\"}")
                .append(",{\"x-amz-date\":\"").append(timestamp).append("\"}");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", objectKey);
        fields.put("Content-Type", contentType);
        fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", timestamp);
        if (credentials instanceof AwsSessionCredentials sessionCredentials) {
            fields.put("x-amz-security-token", sessionCredentials.sessionToken());
            conditions.append(",{\"x-amz-security-token\":\"")
                    .append(sessionCredentials.sessionToken()).append("\"}");
        }
        String policy = "{\"expiration\":\"" + expiration + "\",\"conditions\":" + conditions + "]}";
        String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
        fields.put("policy", encodedPolicy);
        fields.put("x-amz-signature", signPostPolicy(credentials.secretAccessKey(), date, encodedPolicy));
        return Map.copyOf(fields);
    }

    private String signPostPolicy(String secretAccessKey, String date, String encodedPolicy) {
        try {
            byte[] dateKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
            byte[] regionKey = hmac(dateKey, properties.region());
            byte[] serviceKey = hmac(regionKey, "s3");
            byte[] signingKey = hmac(serviceKey, "aws4_request");
            byte[] signature = hmac(signingKey, encodedPolicy);
            StringBuilder hex = new StringBuilder(signature.length * 2);
            for (byte value : signature) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 업로드 정책 생성에 실패했습니다", exception);
        }
    }

    private byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    // 프론트가 S3 POST 직후 호출한다. 임시 객체를 검사한 뒤 최종 key로 복사한다.
    // 최종 key에는 Presigned POST 정책을 발급하지 않으므로, 이후 객체를 덮어쓸 수 없다.
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
            // HeadObject는 항상 ETag를 준다. 없으면 S3 쪽 이상이므로, If-Match 없이
            // 진행해 검사와 복사가 갈라지게 두지 않고 여기서 끊는다.
            String eTag = object.eTag();
            if (eTag == null || eTag.isBlank()) {
                log.error("S3 HeadObject 응답에 ETag가 없어 업로드 확정을 중단합니다: key={}", temporaryObjectKey);
                throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
            }
            validateImageSignature(temporaryObjectKey, contentType, eTag);

            String objectKey = purpose.createObjectKey(
                    accountId,
                    EXTENSIONS_BY_CONTENT_TYPE.get(contentType),
                    UUID.randomUUID().toString()
            );
            s3Client.copyObject(CopyObjectRequest.builder()
                    .copySource(properties.bucket() + "/" + temporaryObjectKey)
                    .copySourceIfMatch(eTag)
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    // COPY면 S3가 원본 메타데이터를 그대로 복사해 위 contentType 지정이 무시된다.
                    // 정규화·검증을 마친 값을 최종 객체에 실제로 반영하려면 REPLACE여야 한다.
                    .metadataDirective("REPLACE")
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
            if (exception.statusCode() == 412) {
                throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
            }
            if (exception.statusCode() == 404) {
                return fileObjectLifecycleService.findReusableConfirmedObjectKey(
                                accountId, purpose, temporaryObjectKey)
                        .map(FileUploadConfirmResponseDto::new)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
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
            rejectSignedUrl(objectKey);
            return;
        }
        if (objectKey == null || objectKey.isBlank()
                || objectKey.contains("..") || objectKey.contains("\\")
                || !purpose.owns(objectKey, accountId)) {
            throw new ForbiddenException(ErrorCode.FILE_ACCESS_DENIED);
        }
        if (objectKey.length() > MAX_OBJECT_KEY_LENGTH) {
            throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
        }
        fileObjectLifecycleService.attach(accountId, purpose, objectKey);
    }

    // 이미지 행이 삭제된 같은 트랜잭션에서 상태만 정리한다. 실제 S3 I/O는 스케줄러가 커밋 후 수행한다.
    public void scheduleAttachedObjectCleanup(String objectKey) {
        if (isFinalObjectKey(objectKey)) {
            fileObjectLifecycleService.detach(objectKey);
        }
    }

    // 여러 이미지를 한꺼번에 정리할 때 쓴다. 한 건씩 부르면 이미지 수만큼
    // SELECT ... FOR UPDATE가 나가고, 잠금 순서가 표시 순서에 끌려간다.
    public void scheduleAttachedObjectCleanup(Collection<String> objectKeys) {
        fileObjectLifecycleService.detachAll(objectKeys.stream()
                .filter(this::isFinalObjectKey)
                .toList());
    }

    private boolean isLegacyHttpsUrl(String value) {
        return value != null && value.startsWith("https://");
    }

    private void rejectSignedUrl(String value) {
        try {
            String query = java.net.URI.create(value).getQuery();
            if (query != null) {
                for (String parameter : query.split("&")) {
                    String name = parameter.split("=", 2)[0];
                    if (name.equalsIgnoreCase("X-Amz-Signature")
                            || name.equalsIgnoreCase("AWSAccessKeyId")
                            || name.equalsIgnoreCase("Signature")) {
                        throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
        }
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

    private void validateImageSignature(String objectKey, String contentType, String eTag) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .range("bytes=0-15")
                            .ifMatch(eTag)
                            .build(),
                    ResponseTransformer.toBytes());
            byte[] header = bytes.asByteArray();
            if (!matchesImageSignature(header, contentType)) {
                throw new BadRequestException(ErrorCode.FILE_UPLOAD_INVALID);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (S3Exception exception) {
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
