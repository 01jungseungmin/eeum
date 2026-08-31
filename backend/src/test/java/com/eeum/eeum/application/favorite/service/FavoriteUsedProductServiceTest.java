package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.dto.response.FavoriteUsedProductResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteUsedProductRow;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Mock private AccountWriteGuard accountWriteGuard;
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
        givenLockedProduct(product());
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeAndRefId(
                ACCOUNT_ID, FavoriteRefType.USED_PRODUCT, PRODUCT_ID)).thenReturn(Optional.empty());
        givenActiveAccount();

        // when
        favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest());

        // then
        verify(usedProductRepository).incrementFavoriteCount(PRODUCT_ID);
        verify(storeRepository, never()).incrementFavoriteCount(any());
    }

    @Test
    void 찜을_해제하면_게시글_찜_수가_감소한다() {
        // given
        givenLockedProduct(product());
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
        // given — 잠금 조회에는 삭제 필터가 없으므로 서비스에서 걸러야 한다
        UsedProduct deleted = product();
        deleted.softDelete();
        givenLockedProduct(deleted);

        // when & then
        assertThatThrownBy(() -> favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        verify(usedProductRepository, never()).incrementFavoriteCount(any());
    }

    @Test
    void 숨김_처리된_게시글은_찜할_수_없다() {
        // given — ID만 알면 목록을 거치지 않고 찜해 favoriteCount를 올릴 수 있다
        UsedProduct hidden = product();
        hidden.hide();
        givenLockedProduct(hidden);

        // when & then — 존재 사실을 흘리지 않도록 NOT_FOUND로 통일한다
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
        givenLockedProduct(sold);
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeAndRefId(
                ACCOUNT_ID, FavoriteRefType.USED_PRODUCT, PRODUCT_ID)).thenReturn(Optional.empty());
        givenActiveAccount();

        // when
        favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest());

        // then
        verify(usedProductRepository).incrementFavoriteCount(PRODUCT_ID);
    }

    @Test
    void 숨김_처리된_게시글의_찜도_해제할_수_있다() {
        // given — 내가 남긴 찜을 거두는 일까지 막으면 카운트가 부풀린 채로 영영 남는다
        UsedProduct hidden = product();
        hidden.hide();
        givenLockedProduct(hidden);
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeAndRefId(
                ACCOUNT_ID, FavoriteRefType.USED_PRODUCT, PRODUCT_ID))
                .thenReturn(Optional.of(Favorite.create(account(), FavoriteRefType.USED_PRODUCT, PRODUCT_ID)));

        // when
        favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest());

        // then
        verify(usedProductRepository).decrementFavoriteCount(PRODUCT_ID);
    }

    @Test
    void 찜_등록은_대상_게시글을_먼저_잠근_뒤_검증한다() {
        // given — 잠그지 않으면 삭제 트랜잭션과 스냅샷이 엇갈려 죽은 찜이 생긴다
        givenLockedProduct(product());
        when(favoriteRepository.findByAccount_AccountIdAndRefTypeAndRefId(
                ACCOUNT_ID, FavoriteRefType.USED_PRODUCT, PRODUCT_ID)).thenReturn(Optional.empty());
        givenActiveAccount();

        // when
        favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest());

        // then — 잠금 없는 조회로 검증하면 안 된다
        verify(usedProductRepository, never()).findByUsedProductIdAndDeletedAtIsNull(any());
    }

    @Test
    void 판매자가_탈퇴한_게시글은_찜할_수_없다() {
        // given — 목록·상세에서 사라진 글이 찜으로만 살아 있으면 안 된다
        UsedProduct product = product();
        product.getSeller().withdraw();
        givenLockedProduct(product);

        // when & then
        assertThatThrownBy(() -> favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        verify(usedProductRepository, never()).incrementFavoriteCount(any());
    }

    @Test
    void 판매자가_탈퇴한_게시글의_찜_수는_조회할_수_없다() {
        UsedProduct product = product();
        product.getSeller().withdraw();
        givenActiveProduct(product);

        assertThatThrownBy(() ->
                favoriteService.getFavoriteCount(FavoriteRefType.USED_PRODUCT, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    // ─────────────────── 찜 수 조회 ───────────────────

    @Test
    void 숨김_게시글의_찜_수는_조회할_수_없다() {
        // given — 비회원도 호출 가능한 공개 API라, 검증 없이 세면 ID를 훑는 것만으로
        // 숨김 게시글의 존재와 찜 수가 드러난다
        UsedProduct hidden = product();
        hidden.hide();
        givenActiveProduct(hidden);

        // when & then
        assertThatThrownBy(() ->
                favoriteService.getFavoriteCount(FavoriteRefType.USED_PRODUCT, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        verify(favoriteRepository, never()).countByRefTypeAndRefId(any(), any());
    }

    @Test
    void 공개_게시글의_찜_수는_그대로_반환한다() {
        givenActiveProduct(product());
        when(favoriteRepository.countByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, PRODUCT_ID))
                .thenReturn(7L);

        long count = favoriteService.getFavoriteCount(FavoriteRefType.USED_PRODUCT, PRODUCT_ID);

        assertThat(count).isEqualTo(7L);
    }

    // ─────────────────── 찜 목록 ───────────────────

    @Test
    void 찜_목록은_대표_사진을_배치로_붙인다() {
        // given — 게시글마다 사진을 조회하면 페이지 크기만큼 쿼리가 나간다(N+1)
        givenFavoriteSlice(row(PRODUCT_ID));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List.of(PRODUCT_ID)))
                .thenReturn(List.of(UsedProductImage.create(product(), "thumb.jpg", 1, true)));

        // when
        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getThumbnailUrl()).isEqualTo("thumb.jpg");
        assertThat(result.getContent().get(0).getUsedProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getContent().get(0).getRegionName()).isEqualTo("역삼동");
        verify(usedProductImageRepository, times(1))
                .findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(any());
    }

    @Test
    void 찜_목록은_조회_결과를_메모리에서_다시_거르지_않는다() {
        // given — 숨김·삭제 필터는 페이징 전에 DB에서 적용된다(FavoriteRepositoryImpl).
        // 조회 후 걸러내면 요청한 size보다 적은 항목이 내려가고 hasNext 판정도 어긋난다.
        givenFavoriteSlice(row(10L), row(11L), row(12L));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(any()))
                .thenReturn(List.of());

        // when
        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        // then — 리포지토리가 내려준 행 수가 그대로 유지된다
        assertThat(result.getContent()).hasSize(3);
        verify(usedProductRepository, never()).findAllById(any());
    }

    @Test
    void 사진이_없는_게시글의_대표_사진은_null이다() {
        givenFavoriteSlice(row(PRODUCT_ID));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List.of(PRODUCT_ID)))
                .thenReturn(List.of());

        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void 찜이_없으면_추가_조회_없이_빈_페이지를_반환한다() {
        givenFavoriteSlice();

        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(ACCOUNT_ID, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(usedProductImageRepository, never())
                .findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(any());
    }

    @Test
    void 빈_목록도_실제_적용된_정렬을_그대로_반환한다() {
        // given — 리포지토리는 요청 sort를 무시하고 최신순으로 고정해 돌려준다.
        // 빈 결과에서만 요청 pageable을 그대로 쓰면 메타데이터가 실제 정렬과 어긋난다.
        Pageable applied = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt")));
        when(favoriteRepository.findFavoriteUsedProducts(any(), any()))
                .thenReturn(new SliceImpl<>(List.of(), applied, false));

        // when — 클라이언트는 가격순을 요청한다
        Slice<FavoriteUsedProductResponseDto> result = favoriteService.getMyFavoriteUsedProducts(
                ACCOUNT_ID, PageRequest.of(0, 20, Sort.by("price")));

        // then
        assertThat(result.getSort()).isEqualTo(applied.getSort());
    }

    @Test
    void 찜_쓰기는_계정_가드를_먼저_통과한다() {
        // given — 탈퇴·정지·가입 미완료 판정은 AccountWriteGuard가 한다(AccountWriteGuardTest).
        // 여기서는 찜 경로가 그 가드를 거치는지만 본다. 건너뛰면 탈퇴 정리 뒤 찜이 되살아난다.
        when(accountWriteGuard.lockActive(ACCOUNT_ID))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN));

        // when & then
        assertThatThrownBy(() -> favoriteService.toggleFavorite(ACCOUNT_ID, toggleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        verify(usedProductRepository, never()).findByUsedProductIdForUpdate(any());
    }

    // ─────────────────── 대상 삭제 CASCADE ───────────────────

    @Test
    void 게시글_삭제로_찜을_정리하면_찜_수도_0이_된다() {
        // given — 찜 행만 지우고 favoriteCount를 두면 삭제된 글이 예전 수를 계속 들고 있어
        // 관리자 통계·정합성 재계산 결과와 어긋난다
        when(favoriteRepository.countByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, PRODUCT_ID))
                .thenReturn(5L);

        // when
        favoriteService.deleteAllByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, PRODUCT_ID);

        // then
        verify(favoriteRepository).deleteAllByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, PRODUCT_ID);
        verify(usedProductRepository).resetFavoriteCount(PRODUCT_ID);
    }

    @Test
    void 상점_삭제로_찜을_정리하면_상점_찜_수도_0이_된다() {
        when(favoriteRepository.countByRefTypeAndRefId(FavoriteRefType.STORE, 7L)).thenReturn(3L);

        favoriteService.deleteAllByRefTypeAndRefId(FavoriteRefType.STORE, 7L);

        verify(storeRepository).resetFavoriteCount(7L);
        verify(usedProductRepository, never()).resetFavoriteCount(any());
    }

    // ─────────────────── 회원 탈퇴 ───────────────────

    @Test
    void 회원이_탈퇴하면_중고_찜_수가_한_번의_IN_UPDATE로_감소한다() {
        // given — 탈퇴자가 남긴 찜이 카운트에 계속 잡히면 안 된다.
        // 찜 1건마다 UPDATE를 날리면 찜이 많은 회원의 탈퇴가 그만큼의 쿼리를 유발한다.
        when(favoriteRepository.findByAccount_AccountIdAndRefType(ACCOUNT_ID, FavoriteRefType.STORE))
                .thenReturn(List.of());
        when(favoriteRepository.findByAccount_AccountIdAndRefType(ACCOUNT_ID, FavoriteRefType.USED_PRODUCT))
                .thenReturn(List.of(
                        Favorite.create(account(), FavoriteRefType.USED_PRODUCT, PRODUCT_ID),
                        Favorite.create(account(), FavoriteRefType.USED_PRODUCT, 11L)));

        // when
        favoriteService.deleteAllByAccountId(ACCOUNT_ID);

        // then
        verify(usedProductRepository).decrementFavoriteCounts(List.of(PRODUCT_ID, 11L));
        verify(usedProductRepository, never()).decrementFavoriteCount(any());
    }

    @Test
    void 찜이_없는_회원_탈퇴는_카운트_UPDATE를_실행하지_않는다() {
        // given — 빈 목록으로 IN 절을 만들면 DB에 따라 문법 오류가 난다
        when(favoriteRepository.findByAccount_AccountIdAndRefType(any(), any()))
                .thenReturn(List.of());

        // when
        favoriteService.deleteAllByAccountId(ACCOUNT_ID);

        // then
        verify(usedProductRepository, never()).decrementFavoriteCounts(any());
        verify(storeRepository, never()).decrementFavoriteCounts(any());
        verify(favoriteRepository).deleteAllByAccount_AccountId(ACCOUNT_ID);
    }

    // ─────────────────── 헬퍼 ───────────────────

    // 쓰기 경로(토글)는 계정 → 대상 순으로 잠근다. 잠금 조회에는 삭제 필터가 없다.
    private void givenLockedProduct(UsedProduct product) {
        givenActiveAccount();
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));
    }

    private void givenActiveAccount() {
        when(accountWriteGuard.lockActive(ACCOUNT_ID)).thenReturn(account());
    }

    private void givenActiveProduct(UsedProduct product) {
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));
    }

    private void givenFavoriteSlice(FavoriteUsedProductRow... rows) {
        when(favoriteRepository.findFavoriteUsedProducts(any(), any()))
                .thenReturn(new SliceImpl<>(List.of(rows)));
    }

    private FavoriteUsedProductRow row(Long usedProductId) {
        return new FavoriteUsedProductRow(
                usedProductId + 100, LocalDateTime.now(),
                usedProductId, "자전거 팝니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"),
                UsedProductStatus.SELLING, "역삼동");
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
