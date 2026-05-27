package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.store.dto.request.StoreNoticeRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreStatusUpdateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreUpdateRequestDto;
import com.eeum.eeum.application.store.dto.response.StoreNoticeResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreResponseDto;
import com.eeum.eeum.application.store.service.StoreImageService;
import com.eeum.eeum.application.store.service.StoreService;
import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Owner - Store", description = "사장 상점 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/stores/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerStoreController {

    private final StoreService storeService;
    private final StoreImageService storeImageService;

    @Operation(
            summary = "내 상점 조회",
            description = "현재 로그인한 사장의 상점 정보를 조회합니다. 상점명, 주소, 연락처, 업종, 설명, 영업시간, 상태, 평점, 찜 수, 리뷰 수 등을 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<StoreResponseDto>> getMyStore() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(storeService.getMyStore(accountId)));
    }

    @Operation(
            summary = "상점 정보 수정",
            description = "현재 로그인한 사장의 상점 기본 정보를 수정합니다. 상점명, 주소, 연락처, 설명, 영업시간, 업종 카테고리를 수정할 수 있습니다."
    )
    @PatchMapping
    public ResponseEntity<ApiResponse<StoreResponseDto>> updateStore(
            @Valid @RequestBody StoreUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(storeService.updateStore(accountId, request)));
    }

    @Operation(
            summary = "상점 상태 변경",
            description = "현재 로그인한 사장의 상점 영업 상태를 변경합니다. OPEN(영업중), TEMP_CLOSED(임시 휴업/쉬는 시간), CLOSED(영업 종료) 상태로 변경할 수 있습니다."
    )
    @PatchMapping("/status")
    public ResponseEntity<ApiResponse<Void>> updateStoreStatus(
            @Valid @RequestBody StoreStatusUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeService.updateStoreStatus(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // OwnerStoreController에 추가
    @Operation(summary = "상점 이미지 목록 조회")
    @GetMapping("/images")
    public ResponseEntity<ApiResponse<List<ImageResponseDto>>> getStoreImages() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeImageService.getImages(accountId)));
    }

    @Operation(summary = "상점 이미지 등록 (최대 20장)")
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<List<ImageResponseDto>>> addStoreImages(
            @Valid @RequestBody ImageUploadListRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeImageService.addImages(accountId, request)
        ));
    }

    @Operation(summary = "상점 이미지 삭제")
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteStoreImage(
            @PathVariable Long imageId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeImageService.deleteImage(accountId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "상점 대표 이미지 설정")
    @PatchMapping("/images/{imageId}/thumbnail")
    public ResponseEntity<ApiResponse<Void>> setStoreThumbnail(
            @PathVariable Long imageId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeImageService.setThumbnail(accountId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "상점 공지 목록 조회",
            description = "현재 로그인한 사장의 상점 공지 목록을 조회합니다. 비활성화된 공지는 제외되며, 상단 고정 공지가 먼저 노출되고 이후 최신 등록순으로 정렬됩니다."
    )
    @GetMapping("/notices")
    public ResponseEntity<ApiResponse<List<StoreNoticeResponseDto>>> getNotices() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(storeService.getNotices(accountId)));
    }

    @Operation(
            summary = "상점 공지 등록",
            description = "현재 로그인한 사장의 상점에 새 공지를 등록합니다. 공지 제목, 공지 내용, 상단 고정 여부를 입력할 수 있습니다."
    )
    @PostMapping("/notices")
    public ResponseEntity<ApiResponse<StoreNoticeResponseDto>> createNotice(
            @Valid @RequestBody StoreNoticeRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(storeService.createNotice(accountId, request)));
    }

    @Operation(
            summary = "상점 공지 수정",
            description = "현재 로그인한 사장의 상점 공지를 수정합니다. 본인 상점의 공지만 수정할 수 있으며, 제목, 내용, 상단 고정 여부를 변경할 수 있습니다."
    )
    @PatchMapping("/notices/{noticeId}")
    public ResponseEntity<ApiResponse<StoreNoticeResponseDto>> updateNotice(
            @PathVariable Long noticeId,
            @Valid @RequestBody StoreNoticeRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeService.updateNotice(accountId, noticeId, request)));
    }

    @Operation(
            summary = "상점 공지 삭제",
            description = "현재 로그인한 사장의 상점 공지를 삭제합니다. 실제 데이터를 즉시 삭제하지 않고 비활성화 처리하여 목록 조회에서 제외합니다."
    )
    @DeleteMapping("/notices/{noticeId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(
            @PathVariable Long noticeId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeService.deleteNotice(accountId, noticeId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}