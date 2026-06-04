package com.eeum.eeum.api.favorite;

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
            summary = "Store.favoriteCount 정합성 재계산",
            description = """
                    favorite 테이블 실제 row 수를 기준으로 모든 상점의 favoriteCount를 재계산합니다.
                    장애 복구, 대량 삭제, 수동 DB 수정 후 정합성이 어긋났을 때 사용하세요.
                    단일 UPDATE ... SELECT로 처리되므로 N번 쿼리 없이 전체 동기화됩니다.
                    """
    )
    @PostMapping("/recalculate")
    public ResponseEntity<ApiResponse<Integer>> recalculateFavoriteCounts() {
        int updated = adminFavoriteService.recalculateFavoriteCounts();
        return ResponseEntity.ok(ApiResponse.success(updated));
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