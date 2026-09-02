package com.eeum.eeum.api.favorite;

import com.eeum.eeum.application.favorite.dto.request.FavoriteBatchCheckRequestDto;
import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.dto.response.*;
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "13. Favorite", description = "찜 API (상점 / 중고상품)")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FavoriteController {

    private final FavoriteService favoriteService;

    // ===================== 찜 토글 / 삭제 =====================

    @Operation(
            summary = "찜 등록/해제 토글, 해제 토글 == 아이콘 클릭 시",
            description = """
                    이미 찜한 상태면 해제, 없으면 등록합니다.
                    응답의 `favorited` 필드로 현재 상태를 확인할 수 있습니다.
                    refType: STORE | USED_PRODUCT
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteToggleResponseDto>> toggleFavorite(
            @RequestBody @Valid FavoriteToggleRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.toggleFavorite(accountId, request)));
    }

    @Operation(
            summary = "찜 삭제 (favoriteId 기반), 내 찜 목록용",
            description = "favoriteId로 찜을 직접 삭제합니다. 내 찜 목록처럼 favoriteId를 이미 아는 경우에 사용합니다."
    )
    @DeleteMapping("/{favoriteId}")
    public ResponseEntity<ApiResponse<Void>> deleteFavorite(@PathVariable @Positive Long favoriteId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        favoriteService.deleteFavorite(accountId, favoriteId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "찜 삭제 (refType + refId 기반), 상세/목록 화면용",
            description = """
                    refType + refId로 찜을 삭제합니다.
                    상점 상세 화면처럼 storeId만 알고 favoriteId를 모를 때 사용합니다.
                    사전에 /favorites/check 호출 없이 바로 삭제할 수 있습니다.
                    예: DELETE /favorites?refType=STORE&refId=3
                    """
    )
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteFavoriteByRef(
            @RequestParam FavoriteRefType refType,
            @RequestParam @Positive Long refId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        favoriteService.deleteFavoriteByRef(accountId, refType, refId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ===================== 내 찜 목록 조회 =====================

    @Operation(
            summary = "내 찜 전체 목록",
            description = "내가 찜한 전체 목록을 최신순으로 반환합니다. refType 정보만 포함하는 경량 응답입니다. " +
                    "커서 무한 스크롤입니다. 첫 페이지는 커서 없이 요청하고, 다음 페이지는 직전 응답의 " +
                    "nextCursorValue·nextCursorId를 cursorValue·cursorId에 그대로 담아 보냅니다" +
                    "(둘 중 하나만 보내면 400). 다음 페이지가 없으면 nextCursor 값들은 null입니다. " +
                    "페이지 번호를 쓰지 않는 이유는 목록을 보는 중에 찜을 토글하면 목록이 밀려 " +
                    "경계 항목이 중복되거나 누락되기 때문입니다."
    )
    @GetMapping("/me/all")
    public ResponseEntity<ApiResponse<CursorSlice<FavoriteResponseDto>>> getMyFavorites(
            @Parameter(description = "직전 응답의 nextCursorValue. 첫 페이지면 생략")
            @RequestParam(required = false) String cursorValue,
            @Parameter(description = "직전 응답의 nextCursorId. 첫 페이지면 생략")
            @RequestParam(required = false) @Positive Long cursorId,
            @Parameter(description = "페이지 크기(최대 50)")
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.getMyFavorites(accountId, cursorValue, cursorId, size)));
    }

    /**
     * 내 찜 전체 목록 (레거시 경로).
     *
     * <p>번호 페이징 응답이라 모바일 무한 스크롤 기준({@code Slice})과 어긋나지만, 이미 클라이언트가
     * 쓰고 있어 그대로 얼려둔다. 프론트가 {@code /me/all}로 옮기면 이 핸들러와
     * FavoriteService.getMyFavoritesPaged를 함께 지운다.
     *
     * <p>상점 찜 목록과 달리 기존 URL({@code /me})이 kebab-case 규칙을 지키고 있어 URL만으로는
     * 신·구를 구분할 수 없다. 그래서 타입별 목록({@code /me/store}, {@code /me/used})과 같은 결의
     * {@code /me/all}을 신규 경로로 둔다.
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")   // 레거시 경로가 레거시 서비스 메서드를 부르는 것은 의도된 짝이다
    @Operation(
            summary = "[Deprecated] 내 찜 전체 목록 (번호 페이징)",
            description = "GET /favorites/me/all로 대체되었습니다. 신규 경로는 무한 스크롤용 Slice 응답입니다. " +
                    "이 경로는 기존 클라이언트 호환을 위해 한시적으로 유지되며 예고 후 제거됩니다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<FavoriteResponseDto>>> getMyFavoritesLegacy(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.getMyFavoritesPaged(accountId, pageable)));
    }

    @Operation(
            summary = "상점 찜 목록",
            description = "내가 찜한 상점 목록을 상점 상세 정보(평점, 썸네일 등)와 함께 반환합니다. " +
                    "커서 무한 스크롤입니다. 첫 페이지는 커서 없이 요청하고, 다음 페이지는 직전 응답의 " +
                    "nextCursorValue·nextCursorId를 cursorValue·cursorId에 그대로 담아 보냅니다" +
                    "(둘 중 하나만 보내면 400). 다음 페이지가 없으면 nextCursor 값들은 null입니다. " +
                    "페이지 번호를 쓰지 않는 이유는 목록을 보는 중에 찜을 토글하면 목록이 밀려 " +
                    "경계 항목이 중복되거나 누락되기 때문입니다."
    )
    @GetMapping("/me/store")
    public ResponseEntity<ApiResponse<CursorSlice<FavoriteStoreResponseDto>>> getMyFavoriteStores(
            @Parameter(description = "직전 응답의 nextCursorValue. 첫 페이지면 생략")
            @RequestParam(required = false) String cursorValue,
            @Parameter(description = "직전 응답의 nextCursorId. 첫 페이지면 생략")
            @RequestParam(required = false) @Positive Long cursorId,
            @Parameter(description = "페이지 크기(최대 50)")
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.getMyFavoriteStores(accountId, cursorValue, cursorId, size)));
    }

    /**
     * 상점 찜 목록 (레거시 경로).
     *
     * <p>대문자 {@code /me/STORE}는 URL 규칙(kebab-case) 위반이고 응답도 번호 페이징이지만,
     * 이미 클라이언트가 쓰고 있어 그대로 얼려둔다. 프론트가 {@code /me/store}로 옮기면
     * 이 핸들러와 FavoriteService.getMyFavoriteStoresPaged를 함께 지운다.
     *
     * <p>URL과 응답 타입을 한 번에 바꾸는 이유는 프론트 마이그레이션을 두 번 시키지 않기 위해서다.
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")   // 레거시 경로가 레거시 서비스 메서드를 부르는 것은 의도된 짝이다
    @Operation(
            summary = "[Deprecated] 상점 찜 목록 (번호 페이징)",
            description = "GET /favorites/me/store로 대체되었습니다. 신규 경로는 무한 스크롤용 Slice 응답입니다. " +
                    "이 경로는 기존 클라이언트 호환을 위해 한시적으로 유지되며 예고 후 제거됩니다."
    )
    @GetMapping("/me/STORE")
    public ResponseEntity<ApiResponse<Page<FavoriteStoreResponseDto>>> getMyFavoriteStoresLegacy(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.getMyFavoriteStoresPaged(accountId, pageable)));
    }

    @Operation(
            summary = "중고 게시글 찜 목록",
            description = "내가 찜한 중고 게시글을 대표 사진·가격·거래 상태와 함께 반환합니다. " +
                    "관리자가 숨긴 게시글은 목록에서 제외됩니다. " +
                    "커서 무한 스크롤입니다. 첫 페이지는 커서 없이 요청하고, 다음 페이지는 직전 응답의 " +
                    "nextCursorValue·nextCursorId를 cursorValue·cursorId에 그대로 담아 보냅니다" +
                    "(둘 중 하나만 보내면 400). 다음 페이지가 없으면 nextCursor 값들은 null입니다. " +
                    "페이지 번호를 쓰지 않는 이유는 목록을 보는 중에 찜을 토글하면 목록이 밀려 " +
                    "경계 항목이 중복되거나 누락되기 때문입니다."
    )
    @GetMapping("/me/used")
    public ResponseEntity<ApiResponse<CursorSlice<FavoriteUsedProductResponseDto>>> getMyFavoriteUsedProducts(
            @Parameter(description = "직전 응답의 nextCursorValue. 첫 페이지면 생략")
            @RequestParam(required = false) String cursorValue,
            @Parameter(description = "직전 응답의 nextCursorId. 첫 페이지면 생략")
            @RequestParam(required = false) @Positive Long cursorId,
            @Parameter(description = "페이지 크기(최대 50)")
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.getMyFavoriteUsedProducts(accountId, cursorValue, cursorId, size)));
    }

    // ===================== 찜 여부 확인 =====================

    @Operation(
            summary = "찜 여부 단건 조회",
            description = "특정 대상의 찜 여부를 조회합니다. 상세 화면 진입 시 사용합니다. (?refType=STORE&refId=3)"
    )
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<FavoriteCheckResponseDto>> checkFavorite(
            @RequestParam FavoriteRefType refType,
            @RequestParam @Positive Long refId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.checkFavorite(accountId, refType, refId)));
    }

    @Operation(
            summary = "찜 여부 배치 조회",
            description = """
                    다수 대상의 찜 여부를 한 번에 조회합니다.
                    목록 화면에서 N+1 없이 배치 처리합니다. 최대 100개.
                    """
    )
    @PostMapping("/check/batch")
    public ResponseEntity<ApiResponse<List<FavoriteCheckResponseDto>>> checkFavoritesBatch(
            @RequestBody @Valid FavoriteBatchCheckRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.checkFavoritesBatch(accountId, request)));
    }

    @Operation(
            summary = "대상별 찜 수 조회",
            description = "특정 대상의 총 찜 수를 반환합니다. 비회원도 접근 가능합니다."
    )
    @PreAuthorize("permitAll()")  // 클래스 레벨 isAuthenticated() 오버라이드 — 비회원 허용
    @SecurityRequirements          // 클래스 레벨 bearerAuth 해제 — 문서에도 공개 API로 표시
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> getFavoriteCount(
            @RequestParam FavoriteRefType refType,
            @RequestParam @Positive Long refId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.getFavoriteCount(refType, refId)));
    }
}