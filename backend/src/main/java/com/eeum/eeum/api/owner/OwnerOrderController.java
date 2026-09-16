package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.store.dto.request.OrderRejectRequestDto;
import com.eeum.eeum.application.store.dto.response.StoreOrderResponseDto;
import com.eeum.eeum.application.store.service.StoreOrderService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "18. Owner - Order", description = "사장 주문 관리 API")
@RestController
@RequestMapping("/owner/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerOrderController {

    private final StoreOrderService storeOrderService;

    @Operation(summary = "주문 리스트 조회",
            description = "사장이 주문 리스트를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StoreOrderResponseDto>>> getStoreOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeOrderService.getStoreOrders(accountId, status, pageable)));
    }

    @Operation(summary = "주문 상세 조회",
            description = "사장이 주문 상세 내용을 조회합니다.")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<StoreOrderResponseDto>> getStoreOrderDetail(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeOrderService.getStoreOrderDetail(accountId, orderId)));
    }

    @Operation(summary = "주문 승인",
            description = "사장이 주문을 승인합니다.")
    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmOrder(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeOrderService.confirmOrder(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "픽업 준비 완료",
            description = "사장이 주문을 픽업 준비 완료 상태로 변경합니다.")
    @PatchMapping("/{orderId}/ready")
    public ResponseEntity<ApiResponse<Void>> readyOrder(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeOrderService.readyOrder(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "거래 완료",
            description = "사장이 주문을 거래 완료 상태로 변경합니다.")
    @PatchMapping("/{orderId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeOrder(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeOrderService.completeOrder(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "주문 거절",
            description = "사장이 주문을 거절합니다. 온라인 결제 완료 건은 PortOne 결제 취소가 함께 처리됩니다.")
    @PatchMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRejectRequestDto request) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeOrderService.rejectOrder(accountId, orderId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "환불 승인",
            description = "사장이 고객의 환불 요청을 승인합니다. PortOne 결제 취소가 함께 처리됩니다.")
    @PatchMapping("/{orderId}/refund/approve")
    public ResponseEntity<ApiResponse<Void>> approveRefund(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeOrderService.approveRefund(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "환불 거절",
            description = "사장이 고객의 환불 요청을 거절합니다.")
    @PatchMapping("/{orderId}/refund/reject")
    public ResponseEntity<ApiResponse<Void>> rejectRefund(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeOrderService.rejectRefund(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}