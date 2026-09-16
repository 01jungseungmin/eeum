package com.eeum.eeum.api.favorite;

import com.eeum.eeum.application.favorite.dto.response.FavoriteRecalculateResponseDto;
import com.eeum.eeum.application.favorite.dto.response.FavoriteStatResponseDto;
import com.eeum.eeum.application.favorite.service.AdminFavoriteService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "01. Admin - Favorite", description = "관리자 찜 통계 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/admin/favorites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFavoriteController {

    private final AdminFavoriteService adminFavoriteService;

    @Operation(
            summary = "favoriteCount 정합성 재계산",
            description = """
                    favorite 테이블 실제 row 수를 기준으로 대상의 favoriteCount를 재계산합니다.
                    refType을 지정하면 해당 타입만, 생략하면 상점·중고 게시글 전체를 재계산합니다.

                    ⚠️ 찜 쓰기가 한산한 시간대에 실행하세요.
                    재계산은 타입별 단일 UPDATE로 도는데, 그 사이에 들어온 찜 등록·해제·회원 탈퇴의
                    증감이 과거 시점 count로 덮일 수 있습니다. 잠금으로 막지 않는 이유는,
                    드물게 쓰는 이 보정 작업 때문에 가장 잦은 쓰기인 찜 토글에 상시 비용을 얹게 되기 때문입니다.
                    어긋난 값이 남으면 다시 실행하면 됩니다 — 이 API 자체가 그 목적의 도구입니다.
                    """
    )
    @PostMapping("/recalculate")
    public ResponseEntity<ApiResponse<FavoriteRecalculateResponseDto>> recalculateFavoriteCounts(
            @RequestParam(required = false) FavoriteRefType refType
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminFavoriteService.recalculateFavoriteCounts(refType)));
    }

    @Operation(
            summary = "찜 인기 항목 통계",
            description = "기간 + refType 기준 상위 N개 인기 항목을 조회합니다."
    )
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<List<FavoriteStatResponseDto>>> getFavoriteStats(
            @RequestParam FavoriteRefType refType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminFavoriteService.getFavoriteStats(refType, from, to, limit)));
    }
}