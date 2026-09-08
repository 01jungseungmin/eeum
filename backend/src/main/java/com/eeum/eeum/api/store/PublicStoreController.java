package com.eeum.eeum.api.store;

import com.eeum.eeum.application.product.dto.response.*;
import com.eeum.eeum.application.store.dto.response.StoreDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreListResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreNoticeResponseDto;
import com.eeum.eeum.application.store.service.PublicStoreService;
import com.eeum.eeum.application.search.enums.PopularSearchScope;
import com.eeum.eeum.application.search.service.PopularSearchService;
import com.eeum.eeum.application.search.service.PopularSearchViewerKeyResolver;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.ViewerKeyResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "11. Store", description = "상점 조회 API (비회원 포함)")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class PublicStoreController {

    private final PublicStoreService publicStoreService;
    private final PopularSearchService popularSearchService;
    private final PopularSearchViewerKeyResolver popularSearchViewerKeyResolver;
    private final ViewerKeyResolver viewerKeyResolver;

    @Operation(summary = "상점 목록 조회",
            description = "카테고리, 지역, 키워드로 상점 목록을 조회합니다. 비회원 접근 가능.")
    @GetMapping("/stores")
    public ResponseEntity<ApiResponse<Page<StoreListResponseDto>>> getStores(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<StoreListResponseDto> stores = publicStoreService.getStores(categoryId, regionId, keyword, pageable);
        popularSearchService.record(PopularSearchScope.STORE, keyword, popularSearchViewerKeyResolver.resolve(request));
        return ResponseEntity.ok(ApiResponse.success(stores));
    }

    @Operation(summary = "주변 상점 조회", description = "현재 위치 기준 반경 내 상점을 조회합니다.")
    @GetMapping("/stores/nearby")
    public ResponseEntity<ApiResponse<List<StoreListResponseDto>>> getNearbyStores(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "3.0") double radiusKm,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        List<StoreListResponseDto> stores = publicStoreService.getNearbyStores(
                latitude, longitude, radiusKm, categoryId, regionId, keyword);
        popularSearchService.record(PopularSearchScope.STORE, keyword, popularSearchViewerKeyResolver.resolve(request));
        return ResponseEntity.ok(ApiResponse.success(stores));
    }

    @Operation(summary = "상점 상세 조회",
            description = "상점 상세 정보, 이미지, 공지를 조회합니다. 비회원 접근 가능.")
    @GetMapping("/stores/{storeId}")
    public ResponseEntity<ApiResponse<StoreDetailResponseDto>> getStoreDetail(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStoreService.getStoreDetail(storeId)));
    }

    @Operation(summary = "상점 상품 목록 조회",
            description = "특정 상점의 상품 목록을 조회합니다. 비회원 접근 가능.")
    @GetMapping("/stores/{storeId}/products")
    public ResponseEntity<ApiResponse<List<ProductListResponseDto>>> getStoreProducts(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStoreService.getStoreProducts(storeId)));
    }

    @Operation(summary = "상점 상품 카테고리 조회", description = "특정 상점의 상품 카테고리 목록을 조회합니다.")
    @GetMapping("/stores/{storeId}/product-categories")
    public ResponseEntity<ApiResponse<List<ProductCategoryResponseDto>>> getStoreProductCategories(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStoreService.getStoreProductCategories(storeId)));
    }

    @Operation(summary = "상점 공지 목록 조회", description = "특정 상점의 공지 목록을 조회합니다.")
    @GetMapping("/stores/{storeId}/notices")
    public ResponseEntity<ApiResponse<List<StoreNoticeResponseDto>>> getStoreNotices(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStoreService.getStoreNotices(storeId)));
    }

    @Operation(summary = "상점 이벤트 상품 조회",
            description = "특정 상점의 진행 중인 이벤트 상품을 조회합니다. 비회원 접근 가능.")
    @GetMapping("/stores/{storeId}/event-products")
    public ResponseEntity<ApiResponse<List<EventProductListResponseDto>>> getEventProducts(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStoreService.getEventProducts(storeId)));
    }

    @Operation(
            summary = "상품 상세 조회",
            description = "상품 상세 정보와 이미지를 조회합니다. 비회원 접근 가능하며, 동일 IP 기준 6시간 내 중복 조회수 증가는 제한합니다."
    )
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponseDto>> getProductDetail(
            @PathVariable Long productId,
            HttpServletRequest request
    ) {
        String viewerKey = viewerKeyResolver.resolve(request); // 위임
        return ResponseEntity.ok(ApiResponse.success(
                publicStoreService.getProductDetail(productId, viewerKey)));
    }

    @Operation(summary = "상품 옵션 조회", description = "특정 상품의 옵션 목록을 조회합니다.")
    @GetMapping("/products/{productId}/options")
    public ResponseEntity<ApiResponse<List<ProductOptionDto>>> getProductOptions(
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStoreService.getProductOptions(productId)));
    }
}
