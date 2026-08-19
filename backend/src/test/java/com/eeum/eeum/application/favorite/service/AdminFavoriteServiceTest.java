package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.response.FavoriteRecalculateResponseDto;
import com.eeum.eeum.application.favorite.dto.response.FavoriteStatResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteStatProjection;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 인기 항목 통계 테스트 — 통계 항목 수에 비례해 쿼리가 늘지 않는지에 초점을 둔다.
 */
@ExtendWith(MockitoExtension.class)
class AdminFavoriteServiceTest {

    @Mock private FavoriteRepository favoriteRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private UsedProductRepository usedProductRepository;

    @InjectMocks
    private AdminFavoriteService adminFavoriteService;

    @Test
    void 인기_중고상품_통계는_제목을_한_번의_IN_조회로_붙인다() {
        // given — 항목마다 findById를 호출하면 상위 N개만큼 쿼리가 나간다
        when(favoriteRepository.findFavoriteStats(any(), any(), any(), anyInt()))
                .thenReturn(List.of(stat(10L, 5L), stat(11L, 3L), stat(12L, 1L)));
        when(usedProductRepository.findByUsedProductIdInAndDeletedAtIsNull(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(product(10L, "자전거"), product(11L, "노트북"), product(12L, "의자")));

        // when
        List<FavoriteStatResponseDto> result = adminFavoriteService.getFavoriteStats(
                FavoriteRefType.USED_PRODUCT, LocalDateTime.now().minusDays(7), LocalDateTime.now(), 3);

        // then
        assertThat(result).extracting(FavoriteStatResponseDto::getRefName)
                .containsExactly("자전거", "노트북", "의자");
        verify(usedProductRepository, times(1)).findByUsedProductIdInAndDeletedAtIsNull(any());
        verify(usedProductRepository, never()).findById(any());
    }

    @Test
    void 통계에는_남아_있지만_대상이_삭제된_항목은_삭제_표기로_반환한다() {
        // given — 찜 통계 행은 남고 대상만 사라진 경우.
        // 중고 게시글은 Soft Delete라 deletedAt 조건이 걸린 조회를 써야 삭제 글이 빠진다.
        when(favoriteRepository.findFavoriteStats(any(), any(), any(), anyInt()))
                .thenReturn(List.of(stat(10L, 5L), stat(99L, 2L)));
        when(usedProductRepository.findByUsedProductIdInAndDeletedAtIsNull(List.of(10L, 99L)))
                .thenReturn(List.of(product(10L, "자전거")));

        // when
        List<FavoriteStatResponseDto> result = adminFavoriteService.getFavoriteStats(
                FavoriteRefType.USED_PRODUCT, null, null, 2);

        // then
        assertThat(result).extracting(FavoriteStatResponseDto::getRefName)
                .containsExactly("자전거", "(삭제된 중고상품)");
    }

    @Test
    void 통계가_비어_있으면_이름_조회를_하지_않는다() {
        when(favoriteRepository.findFavoriteStats(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        List<FavoriteStatResponseDto> result = adminFavoriteService.getFavoriteStats(
                FavoriteRefType.STORE, null, null, 10);

        assertThat(result).isEmpty();
        verify(storeRepository, never()).findAllById(any());
        verify(usedProductRepository, never()).findByUsedProductIdInAndDeletedAtIsNull(any());
    }

    // ─────────────────── 정합성 재계산 ───────────────────

    @Test
    void refType을_지정하면_해당_타입만_재계산한다() {
        // given
        when(usedProductRepository.recalculateAllFavoriteCounts()).thenReturn(42);

        // when
        FavoriteRecalculateResponseDto result =
                adminFavoriteService.recalculateFavoriteCounts(FavoriteRefType.USED_PRODUCT);

        // then — 전체 테이블을 갱신하는 무거운 쿼리라 지정하지 않은 타입까지 건드리면 안 된다
        assertThat(result.getTotalUpdated()).isEqualTo(42);
        assertThat(result.getUpdatedRows())
                .containsExactly(entry(FavoriteRefType.USED_PRODUCT, 42));
        verify(storeRepository, never()).recalculateAllFavoriteCounts();
    }

    @Test
    void refType을_생략하면_상점과_중고_게시글을_모두_재계산한다() {
        // given
        when(storeRepository.recalculateAllFavoriteCounts()).thenReturn(12);
        when(usedProductRepository.recalculateAllFavoriteCounts()).thenReturn(30);

        // when
        FavoriteRecalculateResponseDto result = adminFavoriteService.recalculateFavoriteCounts(null);

        // then
        assertThat(result.getUpdatedRows()).containsOnly(
                entry(FavoriteRefType.STORE, 12),
                entry(FavoriteRefType.USED_PRODUCT, 30));
        assertThat(result.getTotalUpdated()).isEqualTo(42);
    }

    // ─────────────────── 헬퍼 ───────────────────

    private FavoriteStatProjection stat(Long refId, Long count) {
        return new FavoriteStatProjection() {
            @Override public Long getRefId() { return refId; }
            @Override public Long getFavoriteCount() { return count; }
        };
    }

    private UsedProduct product(Long id, String title) {
        Account seller = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        UsedProduct product = UsedProduct.create(
                seller, Category.createRoot(CategoryType.USED, "디지털기기", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                title, "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(product, "usedProductId", id);
        return product;
    }
}
