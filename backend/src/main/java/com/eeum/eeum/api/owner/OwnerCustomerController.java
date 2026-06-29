package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.owner.dto.response.OwnerCustomerResponseDto;
import com.eeum.eeum.application.owner.dto.response.OwnerCustomerSummaryResponseDto;
import com.eeum.eeum.application.owner.enums.OwnerCustomerInterestType;
import com.eeum.eeum.application.owner.enums.OwnerCustomerSortType;
import com.eeum.eeum.application.owner.enums.OwnerCustomerType;
import com.eeum.eeum.application.owner.service.OwnerCustomerService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "06. Owner - Store", description = "사장 상점 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/stores/me/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerCustomerController {

    private final OwnerCustomerService ownerCustomerService;

    @Operation(
            summary = "통합 고객 목록 조회",
            description = """
                    내 상점의 전체 고객 목록을 조회합니다.
                    완료 주문 고객, 찜 고객, 채팅 참여 고객의 합집합으로 구성됩니다.

                    **고객 유형 기준**
                    - `POTENTIAL`: 완료 주문 이력 없음, 찜 또는 채팅 참여 이력 있음
                    - `NEW`: 완료 주문 1회
                    - `NORMAL`: 완료 주문 2회 이상
                    - `REGULAR`: 최근 6개월 내 완료 주문 3회 이상

                    **관심 유형 기준**
                    - `FAVORITE`: 상점을 찜한 고객
                    - `CHAT_PARTICIPANT`: 상점 채팅방에 ACTIVE 상태로 참여 중인 고객

                    고객명·전화번호는 마스킹 처리됩니다.
                    N+1 없이 6 쿼리 이내로 처리합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OwnerCustomerResponseDto>>> getCustomers(
            @Parameter(description = "고객 유형 필터 (기본값: ALL)")
            @RequestParam(defaultValue = "ALL") OwnerCustomerType customerType,
            @Parameter(description = "관심 유형 필터 (기본값: ALL)")
            @RequestParam(defaultValue = "ALL") OwnerCustomerInterestType interestType,
            @Parameter(description = "정렬 기준 (기본값: RECENT_ORDER_DESC)")
            @RequestParam(defaultValue = "RECENT_ORDER_DESC") OwnerCustomerSortType sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerCustomerService.getCustomers(ownerId, customerType, interestType, sort, pageable)));
    }

    @Operation(
            summary = "고객 요약 통계 조회",
            description = """
                    내 상점의 고객 유형별 수, 관심 유형별 수, 전체 매출을 요약하여 반환합니다.
                    상단 대시보드 카드에 사용합니다.
                    """
    )
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<OwnerCustomerSummaryResponseDto>> getCustomerSummary() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerCustomerService.getCustomerSummary(ownerId)));
    }
}
