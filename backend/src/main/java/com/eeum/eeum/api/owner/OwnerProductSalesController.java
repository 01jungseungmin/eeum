package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.owner.dto.response.OwnerCategorySalesResponseDto;
import com.eeum.eeum.application.owner.dto.response.OwnerProductSalesResponseDto;
import com.eeum.eeum.application.owner.service.OwnerProductSalesService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "06. Owner - Store", description = "사장 상점 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/stores/me/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerProductSalesController {

    private final OwnerProductSalesService ownerProductSalesService;

    @Operation(
            summary = "상품별 판매 수량 조회",
            description = """
                    내 상점의 상품별 판매 수량 합계를 수량 내림차순으로 조회합니다.

                    **집계 기준**
                    - 수치는 주문 건수가 아니라 판매 수량 합(`sum(quantity)`)입니다
                    - 이벤트 상품 판매분은 원본 상품에 합산되어 한 행으로 나옵니다
                    - 삭제(비활성)된 상품은 제외됩니다
                    - 기간은 주문 생성 시각(`createdAt`) 기준이며 from·to 모두 포함입니다
                    - 판매 이력이 없는 상품은 행 자체가 나오지 않습니다
                    """
    )
    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<List<OwnerProductSalesResponseDto>>> getProductSales(
            @Parameter(description = "집계할 주문 상태. 생략 시 COMPLETED만 집계")
            @RequestParam(required = false) List<OrderStatus> statuses,
            @Parameter(description = "주문 생성 시각 하한(포함). 예: 2026-09-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "주문 생성 시각 상한(포함). 예: 2026-09-30T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerProductSalesService.getProductSales(ownerId, statuses, from, to)));
    }

    @Operation(
            summary = "카테고리별 판매 통계 조회",
            description = """
                    내 상점의 상품 카테고리별 판매 수량·금액 합계와 비율을 수량 내림차순으로 조회합니다.

                    **집계 기준**
                    - 집계 규칙은 상품별 판매 수량 조회(`GET /owner/stores/me/products/sales`)와 같습니다
                    - 판매 금액은 주문 상품의 단가 × 수량 합입니다(옵션 금액 포함)
                    - 이벤트 상품 판매분은 원본 상품의 카테고리로 합산됩니다
                    - 삭제(비활성)된 상품은 제외됩니다
                    - 기간은 주문 생성 시각(`createdAt`) 기준이며 from·to 모두 포함입니다
                    - 판매 이력이 없는 카테고리는 행 자체가 나오지 않습니다
                    - `quantityRatio`·`amountRatio`는 응답에 포함된 카테고리 합계 대비 %이며, 반올림 때문에 합이 정확히 100이 아닐 수 있습니다
                    """
    )
    @GetMapping("/sales/categories")
    public ResponseEntity<ApiResponse<List<OwnerCategorySalesResponseDto>>> getCategorySales(
            @Parameter(description = "집계할 주문 상태. 생략 시 COMPLETED만 집계")
            @RequestParam(required = false) List<OrderStatus> statuses,
            @Parameter(description = "주문 생성 시각 하한(포함). 예: 2026-09-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "주문 생성 시각 상한(포함). 예: 2026-09-30T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerProductSalesService.getCategorySales(ownerId, statuses, from, to)));
    }
}
