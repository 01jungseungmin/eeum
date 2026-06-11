package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.store.dto.response.StoreOrderResponseDto;
import com.eeum.eeum.application.store.service.StoreOrderService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StoreOrderResponseDto>>> getStoreOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeOrderService.getStoreOrders(accountId, status, pageable)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<StoreOrderResponseDto>> getStoreOrderDetail(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeOrderService.getStoreOrderDetail(accountId, orderId)));
    }

    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmOrder(
            @PathVariable Long orderId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeOrderService.confirmOrder(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}