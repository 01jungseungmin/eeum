package com.eeum.eeum.api.order;

import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.application.order.dto.request.RefundRequestDto;
import com.eeum.eeum.application.order.dto.response.OrderPaymentReadyResponseDto;
import com.eeum.eeum.application.order.dto.response.OrderResponseDto;
import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.application.store.dto.response.StoreReviewDetailResponseDto;
import com.eeum.eeum.application.store.service.StoreReviewService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "13. Order", description = "주문 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final StoreReviewService storeReviewService;

    @Operation(
            summary = "주문 생성 및 결제 준비",
            description = """
                    장바구니를 기반으로 주문을 생성합니다.
                    주문 생성 시 재고가 차감됩니다.
                    온라인 결제는 PaymentStatus.PENDING 상태로 생성되며, 응답의 paymentId를 사용해 PortOne 결제를 진행합니다.
                    현장결제는 PaymentStatus.NOT_PAID 상태로 생성됩니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<OrderPaymentReadyResponseDto>> createOrder(
            @Valid @RequestBody OrderCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                orderService.createOrder(accountId, request)
        ));
    }

    @Operation(summary = "내 주문 목록 조회", description = "현재 로그인한 사용자의 주문 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponseDto>>> getMyOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getMyOrders(accountId, pageable)
        ));
    }

    @Operation(summary = "주문 상세 조회", description = "현재 로그인한 사용자의 특정 주문 상세 정보를 조회합니다.")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrderDetail(
            @PathVariable Long orderId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrderDetail(accountId, orderId)
        ));
    }

    @Operation(
            summary = "주문 취소",
            description = "결제 대기 또는 결제 완료 상태의 주문을 취소합니다. 취소 시 주문 생성 시 차감된 재고가 복구됩니다."
    )
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @PathVariable Long orderId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        orderService.cancelOrder(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "주문 환불 요청", description = "현재 로그인한 사용자가 본인의 결제 완료 주문에 대해 환불을 요청합니다. 실제 환불 처리는 승인 이후 진행됩니다.")
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<ApiResponse<Void>> requestRefund(
            @PathVariable Long orderId,
            @Valid @RequestBody RefundRequestDto request) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        orderService.requestOrderRefund(accountId, orderId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "주문 리뷰 조회", description = "내가 작성한 주문 리뷰를 조회합니다.")
    @GetMapping("/{orderId}/review")
    public ResponseEntity<ApiResponse<StoreReviewDetailResponseDto>> getOrderReview(
            @PathVariable Long orderId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.getOrderReview(accountId, orderId)
        ));
    }
}