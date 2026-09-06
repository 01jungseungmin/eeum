package com.eeum.eeum.api.file;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.file.dto.request.FilePresignedUrlRequestDto;
import com.eeum.eeum.application.file.dto.request.FileUploadConfirmRequestDto;
import com.eeum.eeum.application.file.dto.response.FilePresignedGetUrlResponseDto;
import com.eeum.eeum.application.file.dto.response.FilePresignedUrlResponseDto;
import com.eeum.eeum.application.file.dto.response.FileUploadConfirmResponseDto;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "06. File", description = "Private S3 이미지 업로드 API")
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/presigned-url")
    @Operation(summary = "이미지 Presigned PUT URL 발급",
            description = "프론트는 응답 uploadUrl에 실제 이미지 바이트를 PUT하고, headers의 Content-Type을 그대로 넣어야 합니다. "
                    + "PUT 성공 후 /files/confirm으로 업로드를 검증한 다음 objectKey를 기존 이미지 등록 API에 전달합니다.")
    public ResponseEntity<ApiResponse<FilePresignedUrlResponseDto>> createPresignedUploadUrl(
            @Valid @RequestBody FilePresignedUrlRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                fileStorageService.createPresignedUploadUrl(accountId, request)
        ));
    }

    @PostMapping("/confirm")
    @Operation(summary = "S3 이미지 업로드 완료 확인",
            description = "S3 객체의 실제 Content-Type과 크기를 검증합니다. 응답 objectKey만 DB에 저장해야 합니다.")
    public ResponseEntity<ApiResponse<FileUploadConfirmResponseDto>> confirmUpload(
            @Valid @RequestBody FileUploadConfirmRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(fileStorageService.confirmUpload(accountId, request)));
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "내 업로드 이미지 Presigned GET URL 발급",
            description = "본인이 발급받은 objectKey의 업로드 직후 미리보기용입니다. 게시글·채팅 이미지 조회는 각 도메인 권한 검증 후 별도로 발급합니다.")
    public ResponseEntity<ApiResponse<FilePresignedGetUrlResponseDto>> createPresignedGetUrl(
            @RequestParam String objectKey
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                fileStorageService.createOwnedPresignedGetUrl(accountId, objectKey)
        ));
    }
}
