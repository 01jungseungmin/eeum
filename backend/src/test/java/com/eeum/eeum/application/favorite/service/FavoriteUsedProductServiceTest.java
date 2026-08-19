package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.dto.response.FavoriteUsedProductResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 중고 게시글 찜 연동 테스트.
 * 상점 찜 경로는 기존 동작이므로 여기서는 USED_PRODUCT 분기만 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteUsedProductServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Mock private FavoriteRepository favoriteRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreImageRepository storeImageRepository;
    @Mock private UsedProductRepository usedProductRepository;
    @Mock private UsedProductImageRepository usedProductImageRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    // ─────────────────── 찜 등록 / 해제 ───────────────────

    @Test
    void 찜을_등록하면_게시글_찜_수가_원자적으로_증가한다() {
        // given — 읽어서 +1 후 저장하면 동시 찜이 서로의 증가분을 덮어쓴다
        givenActiveProduct(product());
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeAndRefId(
                ACCOUNT_ID, FavoriteRefType.USED_PRODUCT, PRODUCT_ID)).thenReturn(Optional.empty());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account()));

        // when
        favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest());

        // then
        verify(usedProductRepository).incrementFavoriteCount(PRODUCT_ID);
        verify(storeRepository, never()).incrementFavoriteCount(any());
    }

    @Test
    void 찜을_해제하면_게시글_찜_수가_감소한다() {
        // given
        UsedProduct product = product();
        givenActiveProduct(product);
        Favorite favorite = Favorite.create(account(), FavoriteRefType.USED_PRODUCT, PRODUCT_ID);
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeAndRefId(
                ACCOUNT_ID, FavoriteRefType.USED_PRODUCT, PRODUCT_ID)).thenReturn(Optional.of(favorite));

        // when
        favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest());

        // then
        verify(usedProductRepository).decrementFavoriteCount(PRODUCT_ID);
    }

    @Test
    void 삭제된_게시글은_찜할_수_없다() {
        // given — existsById를 쓰면 삭제된 글도 존재로 판정된다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        verify(usedProductRepository, never()).incrementFavoriteCount(any());
    }

    @Test
    void 판매완료된_게시글도_찜할_수_있다() {
        // given — 거래가 끝난 뒤에도 기록으로 남길 수 있어야 한다
        UsedProduct sold = product();
        sold.markSold();
        givenActiveProduct(sold);
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeAndRefId(
                ACCOUNT_ID, FavoriteRefType.USED_PRODUCT, PRODUCT_ID)).thenReturn(Optional.empty());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account()));

        // when
        favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest());

        // then
        verify(usedProductRepository).incrementFavoriteCount(PRODUCT_ID);
    }

    // ─────────────────── 찜 목록 ───────────────────

    @Test
    void 찜_목록은_대표_사진을_배치로_붙인다() {
        // given — 게시글마다 사진을 조회하면 페이지 크기만큼 쿼리가 나간다(N+1)
        UsedProduct product = product();
        givenFavoritePage(product);
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List.of(PRODUCT_ID)))
                .thenReturn(List.of(UsedProductImage.create(product, "thumb.jpg", 1, true)));

        // when
        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getThumbnailUrl()).isEqualTo("thumb.jpg");
        assertThat(result.getContent().get(0).getUsedProductId()).isEqualTo(PRODUCT_ID);
        verify(usedProductImageRepository, times(1))
                .findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(any());
    }

    @Test
    void 숨김_처리된_게시글은_찜_목록에서_빠진다() {
        // given — 찜 목록으로 우회해 보게 되면 관리자 숨김이 무의미해진다
        UsedProduct hidden = product();
        hidden.hide();
        givenFavoritePage(hidden);
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List.of(PRODUCT_ID)))
                .thenReturn(List.of());

        // when
        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void 사진이_없는_게시글의_대표_사진은_null이다() {
        givenFavoritePage(product());
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List.of(PRODUCT_ID)))
                .thenReturn(List.of());

        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void 찜이_없으면_추가_조회_없이_빈_페이지를_반환한다() {
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(usedProductRepository, never()).findAllById(any());
    }

    // ─────────────────── 회원 탈퇴 ───────────────────

    @Test
    void 회원이_탈퇴하면_중고_찜_수도_함께_감소한다() {
        // given — 탈퇴자가 남긴 찜이 카운트에 계속 잡히면 안 된다
        when(favoriteRepository.findByAccount_AccountIdAndRefType(ACCOUNT_ID, FavoriteRefType.STORE))
                .thenReturn(List.of());
        when(favoriteRepository.findByAccount_AccountIdAndRefType(ACCOUNT_ID, FavoriteRefType.USED_PRODUCT))
                .thenReturn(List.of(Favorite.create(account(), FavoriteRefType.USED_PRODUCT, PRODUCT_ID)));

        // when
        favoriteService.deleteAllByAccountId(ACCOUNT_ID);

        // then
        verify(usedProductRepository).decrementFavoriteCount(PRODUCT_ID);
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void givenActiveProduct(UsedProduct product) {
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));
    }

    private void givenFavoritePage(UsedProduct product) {
        Favorite favorite = Favorite.create(account(), FavoriteRefType.USED_PRODUCT, PRODUCT_ID);
        ReflectionTestUtils.setField(favorite, "favoriteId", 99L);
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(new PageImpl<>(List.of(favorite)));
        when(usedProductRepository.findAllById(List.of(PRODUCT_ID))).thenReturn(List.of(product));
    }

    private FavoriteToggleRequestDto toggleRequest() {
        FavoriteToggleRequestDto request = new FavoriteToggleRequestDto();
        ReflectionTestUtils.setField(request, "refType", FavoriteRefType.USED_PRODUCT);
        ReflectionTestUtils.setField(request, "refId", PRODUCT_ID);
        return request;
    }

    private Account account() {
        Account account = Account.createUser(
                "user@test.com", "encoded-pw", "사용자", "사용자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        return account;
    }

    private UsedProduct product() {
        UsedProduct product = UsedProduct.create(
                account(), Category.createRoot(CategoryType.USED, "디지털기기", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(product, "usedProductId", PRODUCT_ID);
        return product;
    }
}
