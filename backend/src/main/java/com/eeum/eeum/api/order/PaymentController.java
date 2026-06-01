package com.eeum.eeum.api.order;

import com.eeum.eeum.application.order.dto.request.PaymentCompleteRequestDto;
import com.eeum.eeum.application.order.service.PaymentService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}