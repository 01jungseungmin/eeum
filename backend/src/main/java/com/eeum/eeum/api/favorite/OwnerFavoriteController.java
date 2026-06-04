package com.eeum.eeum.api.favorite;

import com.eeum.eeum.application.favorite.dto.response.FavoriteCustomerResponseDto;
import com.eeum.eeum.application.favorite.service.OwnerFavoriteService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "06. Owner - Store", description = "사장 상점 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/stores/me/favorite-customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerFavoriteController {

    private final OwnerFavoriteService ownerFavoriteService;

    @Operation(
            summary = "찜 고객 목록 조회",
            description = """
                    내 상점을 찜한 고객 목록을 찜 등록일 최신순으로 조회합니다.
                    고객명은 마스킹 처리("이*민")되어 제공됩니다.
                    완료 주문 수·총 구매액·최근 주문일·평균 평점을 포함합니다.
                    3 쿼리(Favorite + 주문 집계 + 리뷰 집계)로 N+1 없이 처리합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FavoriteCustomerResponseDto>>> getFavoriteCustomers(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerFavoriteService.getFavoriteCustomers(ownerId, pageable)));
    }
}
