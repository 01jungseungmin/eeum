package com.eeum.eeum.api.order;

import com.eeum.eeum.application.order.dto.request.PaymentCompleteRequestDto;
import com.eeum.eeum.application.order.dto.request.RefundRequestDto;
import com.eeum.eeum.application.order.dto.response.PaymentResponseDto;
import com.eeum.eeum.application.order.service.PaymentService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "14. Payment", description = "결제 API")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "결제 완료 검증",
            description = """
                    프론트에서 PortOne 결제 완료 후 호출합니다.
                    paymentId와 orderNumber를 전달받아 서버에서 결제 정보를 검증하고 주문/결제 상태를 갱신합니다.
                    """
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPayment(
            @Valid @RequestBody PaymentCompleteRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        paymentService.verifyPayment(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "PortOne Webhook 처리",
            description = """
                    PortOne에서 결제 상태 변경 시 호출하는 Webhook 엔드포인트입니다.
                    인증 없이 호출될 수 있어야 하므로 SecurityConfig에서 permitAll 처리해야 합니다.
                    Webhook 본문 값은 신뢰하지 않고, 추후 PortOne 결제 단건 조회로 검증합니다.
                    """
    )
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-PortOne-Webhook-Signature", required = false) String portoneSignature,
            @RequestHeader(value = "Portone-Webhook-Signature", required = false) String legacySignature
    ) {
        String signature = portoneSignature != null ? portoneSignature : legacySignature;
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "내 결재 내역 조회", description = "현재 로그인한 사용자의 주문 목록을 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<PaymentResponseDto>>> getMyPayments(
            @PageableDefault(size = 10) Pageable pageable) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getMyPayments(accountId, pageable)));
    }

    @Operation(summary = "내 결재 내역 상세 조회", description = "현재 로그인한 사용자의 결제 상세 내역을 조회합니다.")
    @GetMapping("/me/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getPaymentDetail(
            @PathVariable Long paymentId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPaymentDetail(accountId, paymentId)));
    }

    @Operation(summary = "결제 취소", description = "현재 로그인한 사용자의 결제를 즉시 취소합니다. 결제 상태가 취소 가능한 상태일 때만 처리됩니다.")
    @PatchMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelPayment(
            @PathVariable Long paymentId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        paymentService.cancelPayment(accountId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "환불 요청", description = "현재 로그인한 사용자가 결제한 내역에 대해 환불을 요청합니다. 실제 환불 처리는 승인 이후 진행됩니다.")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<Void>> requestRefund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequestDto request) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        paymentService.requestRefund(accountId, paymentId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}