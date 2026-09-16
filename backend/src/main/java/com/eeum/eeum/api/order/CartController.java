package com.eeum.eeum.api.order;

import com.eeum.eeum.application.order.dto.request.CartItemAddRequestDto;
import com.eeum.eeum.application.order.dto.request.CartItemUpdateRequestDto;
import com.eeum.eeum.application.order.dto.response.CartResponseDto;
import com.eeum.eeum.application.order.service.CartService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "12. Cart", description = "장바구니 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "장바구니 조회", description = "현재 로그인한 사용자의 장바구니를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponseDto>> getCart() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getCart(accountId)
        ));
    }

    @Operation(
            summary = "장바구니 상품 추가",
            description = "상품 또는 이벤트 상품을 장바구니에 추가합니다. MENU 상품은 추가할 수 없으며, SALE과 PREORDER는 한 장바구니에 함께 담을 수 없습니다."
    )
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponseDto>> addItem(
            @Valid @RequestBody CartItemAddRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                cartService.addItem(accountId, request)
        ));
    }

    @Operation(summary = "장바구니 상품 수량 변경", description = "장바구니 상품의 수량을 변경합니다.")
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponseDto>> updateItem(
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                cartService.updateItem(accountId, cartItemId, request)
        ));
    }

    @Operation(summary = "장바구니 상품 삭제", description = "장바구니에서 특정 상품을 삭제합니다.")
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponseDto>> removeItem(
            @PathVariable Long cartItemId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                cartService.removeItem(accountId, cartItemId)
        ));
    }

    @Operation(summary = "장바구니 전체 비우기", description = "현재 로그인한 사용자의 장바구니를 전체 비웁니다.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        cartService.clearCart(accountId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}