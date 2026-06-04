package com.eeum.eeum.api.product;

import com.eeum.eeum.application.product.dto.request.EventProductRequestDto;
import com.eeum.eeum.application.product.dto.response.EventProductResponseDto;
import com.eeum.eeum.application.product.service.EventProductService;
import com.eeum.eeum.common.dto.response.ApiResponse;
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

@Tag(name = "10. Owner - EventProduct", description = "사장 이벤트 상품 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/event-products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class EventProductController {

    private final EventProductService eventProductService;

    @Operation(
            summary = "이벤트 상품 목록 조회",
            description = "현재 로그인한 사장의 상점에 등록된 이벤트 상품 목록을 조회합니다. 진행 예정, 진행 중, 종료, 비활성화, 매진 상태를 함께 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventProductResponseDto>>> getMyEventProducts() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                eventProductService.getMyEventProducts(accountId)));
    }

    @Operation(
            summary = "이벤트 상품 등록",
            description = "현재 로그인한 사장의 상품을 이벤트 상품으로 등록합니다. 본인 상점의 상품만 등록할 수 있으며, 이벤트 가격은 원래 가격보다 낮아야 합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<EventProductResponseDto>> createEventProduct(
            @Valid @RequestBody EventProductRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                eventProductService.createEventProduct(accountId, request)));
    }

    @Operation(
            summary = "이벤트 상품 수정",
            description = "현재 로그인한 사장의 이벤트 상품 정보를 수정합니다. 이벤트 가격, 이벤트 수량, 시작/종료 일시를 변경할 수 있습니다."
    )
    @PatchMapping("/{eventProductId}")
    public ResponseEntity<ApiResponse<EventProductResponseDto>> updateEventProduct(
            @PathVariable Long eventProductId,
            @Valid @RequestBody EventProductRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                eventProductService.updateEventProduct(accountId, eventProductId, request)));
    }

    @Operation(
            summary = "이벤트 상품 삭제",
            description = "이벤트 상품을 실제 삭제하지 않고 비활성화 처리합니다. 비활성화된 이벤트 상품은 사용자 앱 이벤트 목록에서 제외됩니다."
    )
    @DeleteMapping("/{eventProductId}")
    public ResponseEntity<ApiResponse<Void>> deleteEventProduct(
            @PathVariable Long eventProductId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        eventProductService.deleteEventProduct(accountId, eventProductId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}