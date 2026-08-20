package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import java.util.Arrays;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.application.used.dto.request.UsedProductSearchRequestDto;
import com.eeum.eeum.domain.used.repository.UsedProductSearchCondition;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsedProductServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CATEGORY_ID = 100L;
    private static final Long REGION_ID = 1000L;
    private static final Long ACCOUNT_REGION_ID = 500L;

    @Mock private UsedProductRepository usedProductRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRegionRepository accountRegionRepository;
    @Mock private UsedProductImageService usedProductImageService;
    @Mock private UsedProductImageRepository usedProductImageRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private com.eeum.eeum.application.favorite.service.FavoriteService favoriteService;
    @Mock private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private UsedProductService usedProductService;

    // ─────────────────── 등록 ───────────────────

    @Test
    void 인증된_활동_지역이면_게시글이_등록된다() {
        // given
        when(accountRepository.findByIdWithLock(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(SELLER_ID, REGION_ID))
                .thenReturn(Optional.of(accountRegion(true)));
        when(usedProductRepository.save(any(UsedProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UsedProductDetailResponseDto result =
                usedProductService.create(SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000")));

        // then
        assertThat(result.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(result.getRegionId()).isEqualTo(REGION_ID);
        assertThat(result.getPrice()).isEqualByComparingTo("10000");
    }

    @Test
    void 내_활동_지역이_아니면_등록할_수_없다() {
        // given — 검증이 없으면 아무 regionId나 실어 남의 동네에 글을 올릴 수 있다
        when(accountRepository.findByIdWithLock(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(SELLER_ID, REGION_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> usedProductService.create(
                SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_ACCESS_REQUIRED);

        verify(usedProductRepository, never()).save(any());
    }

    @Test
    void GPS_인증이_안_된_지역에는_등록할_수_없다() {
        when(accountRepository.findByIdWithLock(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(SELLER_ID, REGION_ID))
                .thenReturn(Optional.of(accountRegion(false)));

        assertThatThrownBy(() -> usedProductService.create(
                SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_VERIFIED);
    }

    @Test
    void 가게_카테고리로는_중고_게시글을_등록할_수_없다() {
        // given — 조회 자체를 CategoryType.USED로 걸어 가게·비활성 카테고리를 함께 막는다
        when(accountRepository.findByIdWithLock(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedProductService.create(
                SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_CATEGORY);
    }

    // ─────────────────── 조회 ───────────────────

    @Test
    void 삭제된_게시글은_조회되지_않는다() {
        // given — 리포지토리 조회 자체가 deletedAt IS NULL을 포함한다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedProductService.getDetail(SELLER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 숨김_처리된_글은_작성자에게만_보인다() {
        // given
        UsedProduct product = product();
        product.hide();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(usedProductImageService.getImages(PRODUCT_ID)).thenReturn(java.util.List.of());

        // when & then — 작성자는 볼 수 있다
        assertThat(usedProductService.getDetail(SELLER_ID, PRODUCT_ID).isHidden()).isTrue();
    }

    @Test
    void 숨김_처리된_글을_남이_조회하면_숨김_사실을_알리지_않고_404다() {
        // given — 403을 주면 "여기 숨겨진 글이 있다"는 사실이 새어 나간다
        UsedProduct product = product();
        product.hide();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThatThrownBy(() -> usedProductService.getDetail(OTHER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    // ─────────────────── 목록 ───────────────────

    @Test
    void 지역을_지정하지_않으면_내가_선택한_동네만_조회한다() {
        // given — 활동 지역이 2개여도 사용자는 하나를 선택해 쓴다. 둘을 합쳐 보여주지 않는다.
        givenSelectedRegion(true);
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        // when
        usedProductService.getRegionProducts(SELLER_ID, searchRequest(null), PageRequest.of(0, 20));

        // then
        assertThat(capturedCondition().regionId()).isEqualTo(REGION_ID);
    }

    @Test
    void 비회원도_지역을_지정하면_목록을_볼_수_있다() {
        // given — 둘러보기는 열어둔다. 지역 인증은 실제 거래(채팅) 단계에서 요구한다.
        when(regionRepository.existsById(REGION_ID)).thenReturn(true);
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        // when — viewerId가 null인 비로그인 상태
        usedProductService.getRegionProducts(null, searchRequest(REGION_ID), PageRequest.of(0, 20));

        // then
        assertThat(capturedCondition().regionId()).isEqualTo(REGION_ID);
    }

    @Test
    void 지역_인증을_받지_않았어도_선택한_동네_목록은_볼_수_있다() {
        // given — verified=false여도 조회는 막지 않는다
        givenSelectedRegion(false);
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        // when
        usedProductService.getRegionProducts(SELLER_ID, searchRequest(null), PageRequest.of(0, 20));

        // then
        assertThat(capturedCondition().regionId()).isEqualTo(REGION_ID);
    }

    @Test
    void 내_활동_지역이_아닌_동네도_지정해서_둘러볼_수_있다() {
        // given — 조회 단계에서는 소속 여부를 따지지 않는다
        Long otherRegionId = 7777L;
        when(regionRepository.existsById(otherRegionId)).thenReturn(true);
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        usedProductService.getRegionProducts(SELLER_ID, searchRequest(otherRegionId), PageRequest.of(0, 20));

        assertThat(capturedCondition().regionId()).isEqualTo(otherRegionId);
    }

    @Test
    void 존재하지_않는_지역은_조회할_수_없다() {
        when(regionRepository.existsById(REGION_ID)).thenReturn(false);

        assertThatThrownBy(() -> usedProductService.getRegionProducts(null, searchRequest(REGION_ID), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    void 비회원이_지역을_지정하지_않으면_어느_동네인지_알_수_없어_거부한다() {
        assertThatThrownBy(() -> usedProductService.getRegionProducts(null, searchRequest(null), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_REGION_REQUIRED);
    }

    @Test
    void 선택한_동네가_없는_회원도_지역을_지정하지_않으면_거부한다() {
        Account account = seller();
        ReflectionTestUtils.setField(account, "primaryRegionId", null);
        when(accountRepository.findById(SELLER_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> usedProductService.getRegionProducts(SELLER_ID, searchRequest(null), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_REGION_REQUIRED);
    }

    @Test
    void 지역을_지정하지_않으면_선택한_동네에_게시글이_등록된다() {
        // given — 앱에서 동네를 고른 뒤 글을 쓰는 흐름과 맞춘다
        // 등록은 계정 행을 잠그고, 선택한 동네 조회는 잠금 없이 다시 읽는다
        when(accountRepository.findByIdWithLock(SELLER_ID)).thenReturn(Optional.of(sellerWithPrimary()));
        when(accountRepository.findById(SELLER_ID)).thenReturn(Optional.of(sellerWithPrimary()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(ACCOUNT_REGION_ID, SELLER_ID))
                .thenReturn(Optional.of(accountRegion(true)));
        when(usedProductRepository.save(any(UsedProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UsedProductCreateRequestDto request = createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(request, "regionId", null);

        // when
        UsedProductDetailResponseDto result = usedProductService.create(SELLER_ID, request);

        // then
        assertThat(result.getRegionId()).isEqualTo(REGION_ID);
    }

    @Test
    void 목록의_대표_사진은_한_번의_조회로_모아_붙인다() {
        // given — 게시글마다 사진을 조회하면 페이지 크기만큼 쿼리가 나간다(N+1)
        UsedProduct product = product();
        givenSelectedRegion(true);
        when(usedProductRepository.search(any(), any()))
                .thenReturn(new SliceImpl<>(List.of(product), PageRequest.of(0, 20), false));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List.of(PRODUCT_ID)))
                .thenReturn(List.of(UsedProductImage.create(product, "thumb.jpg", 1, true)));

        // when
        Slice<UsedProductSummaryResponseDto> result =
                usedProductService.getRegionProducts(SELLER_ID, searchRequest(null), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getThumbnailUrl()).isEqualTo("thumb.jpg");
        verify(usedProductImageRepository, times(1))
                .findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(any());
    }

    @Test
    void 사진이_없는_게시글의_대표_사진은_null이다() {
        givenSelectedRegion(true);
        when(usedProductRepository.search(any(), any()))
                .thenReturn(new SliceImpl<>(List.of(product()), PageRequest.of(0, 20), false));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List.of(PRODUCT_ID)))
                .thenReturn(List.of());

        Slice<UsedProductSummaryResponseDto> result =
                usedProductService.getRegionProducts(SELLER_ID, searchRequest(null), PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void 검색_조건은_지역만_서버가_정하고_나머지는_그대로_전달된다() {
        // given — 필터는 사용자가 고른 값이므로 서버가 손대지 않는다
        givenSelectedRegion(true);
        givenUsedCategories(rootCategory());
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        UsedProductSearchRequestDto request = new UsedProductSearchRequestDto(
                null, "자전거", CATEGORY_ID, UsedProductPriceType.FIXED,
                new BigDecimal("10000"), new BigDecimal("50000"),
                List.of(UsedProductStatus.SELLING, UsedProductStatus.RESERVED));

        // when
        usedProductService.getRegionProducts(SELLER_ID, request, PageRequest.of(0, 20));

        // then
        UsedProductSearchCondition applied = capturedCondition();
        assertThat(applied.regionId()).isEqualTo(REGION_ID);   // 서버가 채운 값
        assertThat(applied.keyword()).isEqualTo("자전거");
        assertThat(applied.categoryIds()).containsExactly(CATEGORY_ID);
        assertThat(applied.priceType()).isEqualTo(UsedProductPriceType.FIXED);
        assertThat(applied.minPrice()).isEqualByComparingTo("10000");
        assertThat(applied.maxPrice()).isEqualByComparingTo("50000");
        assertThat(applied.statuses())
                .containsExactly(UsedProductStatus.SELLING, UsedProductStatus.RESERVED);
    }

    @Test
    void 지역을_직접_지정하면_그_지역이_검색_조건에_담긴다() {
        // given
        Long otherRegionId = 7777L;
        when(regionRepository.existsById(otherRegionId)).thenReturn(true);
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        // when
        usedProductService.getRegionProducts(
                null,
                new UsedProductSearchRequestDto(otherRegionId, "책상", null, null, null, null, null),
                PageRequest.of(0, 20));

        // then
        UsedProductSearchCondition applied = capturedCondition();
        assertThat(applied.regionId()).isEqualTo(otherRegionId);
        assertThat(applied.keyword()).isEqualTo("책상");
    }

    @Test
    void 상위_카테고리를_고르면_하위_카테고리_글까지_함께_조회된다() {
        // given — "디지털기기"를 골랐는데 그 아래 "휴대폰" 글이 빠지면 검색이 고장 난 것처럼 보인다
        Category root = rootCategory();                     // 100
        Category child = childCategory(200L, CATEGORY_ID);  // 200 ← 100
        Category grandChild = childCategory(300L, 200L);    // 300 ← 200
        Category unrelated = childCategory(400L, 999L);     // 다른 트리

        givenSelectedRegion(true);
        givenUsedCategories(root, child, grandChild, unrelated);
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        // when
        usedProductService.getRegionProducts(
                SELLER_ID, searchRequestWithCategory(CATEGORY_ID), PageRequest.of(0, 20));

        // then — 3단계까지 펼쳐지고 다른 트리는 섞이지 않는다
        assertThat(capturedCondition().categoryIds())
                .containsExactlyInAnyOrder(CATEGORY_ID, 200L, 300L);
    }

    @Test
    void 최하위_카테고리를_고르면_자기_자신만_조회된다() {
        Category root = rootCategory();
        Category child = childCategory(200L, CATEGORY_ID);

        givenSelectedRegion(true);
        givenUsedCategories(root, child);
        when(usedProductRepository.search(any(), any())).thenReturn(emptySlice());

        usedProductService.getRegionProducts(
                SELLER_ID, searchRequestWithCategory(200L), PageRequest.of(0, 20));

        assertThat(capturedCondition().categoryIds()).containsExactly(200L);
    }

    @Test
    void 중고_카테고리가_아닌_ID로는_검색할_수_없다() {
        // given — 조용히 빈 목록을 주면 오타를 눈치채지 못한다
        givenSelectedRegion(true);
        givenUsedCategories(rootCategory());

        assertThatThrownBy(() -> usedProductService.getRegionProducts(
                SELLER_ID, searchRequestWithCategory(9999L), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_CATEGORY);
    }

    @Test
    void 판매자가_탈퇴하면_게시글_상세도_볼_수_없다() {
        // given — 탈퇴자에게 거래 문의가 계속 가는 것을 막는다.
        // 탈퇴 계정은 로그인 자체가 막히므로 "작성자에게만 보인다" 예외도 성립하지 않는다.
        UsedProduct product = product();
        product.getSeller().withdraw();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> usedProductService.getDetail(SELLER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    // ─────────────────── 조회수 ───────────────────

    @Test
    void 남의_글을_보면_조회수가_원자_UPDATE로_증가한다() {
        // given — 읽어서 +1 후 저장하면 동시 조회가 서로의 증가분을 덮어쓴다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));
        when(usedProductImageService.getImages(PRODUCT_ID)).thenReturn(List.of());

        // when
        usedProductService.getDetailAndCountView(OTHER_ID, PRODUCT_ID);

        // then
        verify(usedProductRepository).increaseViewCount(PRODUCT_ID);
    }

    @Test
    void 조회수_증가_후_엔티티를_refresh해_응답에_반영한다() {
        // given — 조회수는 QueryDSL bulk UPDATE라 영속성 컨텍스트를 거치지 않는다.
        // 다시 조회해도 1차 캐시의 기존 인스턴스가 나오므로 refresh가 없으면 응답이 항상 1 작다.
        // 실제 값이 반영되는지는 Mock으로 확인할 수 없어 UsedProductDynamicUpdateIntegrationTest에서 검증한다.
        UsedProduct product = product();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(usedProductImageService.getImages(PRODUCT_ID)).thenReturn(List.of());

        // when
        usedProductService.getDetailAndCountView(OTHER_ID, PRODUCT_ID);

        // then
        InOrder inOrder = inOrder(usedProductRepository, entityManager);
        inOrder.verify(usedProductRepository).increaseViewCount(PRODUCT_ID);
        inOrder.verify(entityManager).refresh(product);
    }

    @Test
    void 판매자_본인_조회는_refresh도_하지_않는다() {
        UsedProduct product = product();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(usedProductImageService.getImages(PRODUCT_ID)).thenReturn(List.of());

        usedProductService.getDetailAndCountView(SELLER_ID, PRODUCT_ID);

        verify(entityManager, never()).refresh(any());
    }

    @Test
    void 판매자가_자기_글을_봐도_조회수는_오르지_않는다() {
        // given — 새로고침할 때마다 오르면 지표가 무의미해진다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));
        when(usedProductImageService.getImages(PRODUCT_ID)).thenReturn(List.of());

        // when
        usedProductService.getDetailAndCountView(SELLER_ID, PRODUCT_ID);

        // then
        verify(usedProductRepository, never()).increaseViewCount(any());
    }

    @Test
    void 비회원_상세_조회도_조회수가_오른다() {
        // given — 판매자 본인만 제외한다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));
        when(usedProductImageService.getImages(PRODUCT_ID)).thenReturn(List.of());

        // when
        usedProductService.getDetailAndCountView(null, PRODUCT_ID);

        // then
        verify(usedProductRepository).increaseViewCount(PRODUCT_ID);
    }

    // ─────────────────── 수정 ───────────────────

    @Test
    void 작성자는_게시글을_수정할_수_있다() {
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(usedProductImageService.getImages(PRODUCT_ID)).thenReturn(java.util.List.of());

        UsedProductDetailResponseDto result = usedProductService.update(
                SELLER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.NEGOTIABLE, null));

        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getPriceType()).isEqualTo(UsedProductPriceType.NEGOTIABLE);
        assertThat(result.getPrice()).isNull();
    }

    @Test
    void 수정_경로로도_가격_규칙을_우회할_수_없다() {
        // given — NEGOTIABLE인데 금액이 실려 오면 거부한다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));

        assertThatThrownBy(() -> usedProductService.update(
                SELLER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.NEGOTIABLE, new BigDecimal("5000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_PRICE);
    }

    @Test
    void 남의_게시글은_수정할_수_없다() {
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> usedProductService.update(
                OTHER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.FIXED, new BigDecimal("100"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
    }

    @Test
    void 숨김_게시글은_비소유자에게_수정_요청에도_없는_것으로_응답한다() {
        // given — 상세 조회는 404인데 수정만 403이면 그 차이로 숨김 글의 존재가 드러난다
        UsedProduct hidden = product();
        hidden.hide();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> usedProductService.update(
                OTHER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.FIXED, new BigDecimal("100"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 숨김_게시글도_작성자_본인은_수정할_수_있다() {
        // given — 숨김은 노출 정책이지 작성자의 편집권 박탈이 아니다
        UsedProduct hidden = product();
        hidden.hide();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(hidden));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));

        usedProductService.update(
                SELLER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.FIXED, new BigDecimal("100")));

        assertThat(hidden.getPrice()).isEqualByComparingTo(new BigDecimal("100"));
    }

    // ─────────────────── 삭제 ───────────────────

    @Test
    void 삭제하면_물리_삭제_대신_deletedAt이_찍힌다() {
        // given — 후기·채팅·신고 이력이 가리킬 대상을 남겨야 한다
        UsedProduct product = product();
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        // when
        usedProductService.delete(SELLER_ID, PRODUCT_ID);

        // then
        assertThat(product.isDeleted()).isTrue();
        verify(usedProductRepository, never()).delete(any());
        verify(usedProductRepository, never()).deleteById(eq(PRODUCT_ID));

        // 남겨두면 다른 사용자의 찜 목록에 사라진 글이 계속 남는다
        verify(favoriteService).deleteAllByRefTypeAndRefId(
                com.eeum.eeum.domain.favorite.enums.FavoriteRefType.USED_PRODUCT, PRODUCT_ID);
    }

    @Test
    void 예약_중인_게시글은_삭제할_수_없다() {
        // given — 상대가 거래를 기다리는 중이다. 말없이 사라지면 이유를 알 수 없다.
        UsedProduct product = product();
        product.reserve();
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThatThrownBy(() -> usedProductService.delete(SELLER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_DELETE_NOT_ALLOWED);

        assertThat(product.isDeleted()).isFalse();
        verify(favoriteService, never()).deleteAllByRefTypeAndRefId(any(), any());
    }

    @Test
    void 판매완료된_게시글은_삭제할_수_있다() {
        UsedProduct product = product();
        product.markSold();
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        usedProductService.delete(SELLER_ID, PRODUCT_ID);

        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    void 삭제는_신고_조치와_같은_비관적_잠금으로_게시글을_읽는다() {
        // given — 잠그지 않으면 삭제 직후 들어온 찜이 정리를 지나쳐 죽은 찜으로 남는다
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product()));

        // when
        usedProductService.delete(SELLER_ID, PRODUCT_ID);

        // then
        verify(usedProductRepository, never()).findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID);
        verify(favoriteService).deleteAllByRefTypeAndRefId(
                com.eeum.eeum.domain.favorite.enums.FavoriteRefType.USED_PRODUCT, PRODUCT_ID);
    }

    @Test
    void 남의_게시글은_삭제할_수_없다() {
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> usedProductService.delete(OTHER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
    }

    // ─────────────────── 헬퍼 ───────────────────

    // 선택한 동네(대표 지역)가 설정된 상태를 만든다.
    // Account.primaryRegionId에는 regionId가 아니라 accountRegionId가 들어간다.
    private void givenSelectedRegion(boolean verified) {
        when(accountRepository.findById(SELLER_ID)).thenReturn(Optional.of(sellerWithPrimary()));
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(ACCOUNT_REGION_ID, SELLER_ID))
                .thenReturn(Optional.of(accountRegion(verified)));
    }

    private Account sellerWithPrimary() {
        Account account = seller();
        ReflectionTestUtils.setField(account, "primaryRegionId", ACCOUNT_REGION_ID);
        return account;
    }

    private void givenUsedCategories(Category... categories) {
        when(categoryRepository.findAllByTypeOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(
                CategoryType.USED)).thenReturn(Arrays.asList(categories));
    }

    private Category rootCategory() {
        Category category = Category.createRoot(CategoryType.USED, "디지털기기", 1);
        ReflectionTestUtils.setField(category, "categoryId", CATEGORY_ID);
        return category;
    }

    private Category childCategory(Long categoryId, Long parentId) {
        Category category = Category.createChild(CategoryType.USED, parentId, "휴대폰", 1, 2);
        ReflectionTestUtils.setField(category, "categoryId", categoryId);
        return category;
    }

    private UsedProductSearchRequestDto searchRequestWithCategory(Long categoryId) {
        return new UsedProductSearchRequestDto(null, null, categoryId, null, null, null, null);
    }

    private UsedProductSearchRequestDto searchRequest(Long regionId) {
        return new UsedProductSearchRequestDto(regionId, null, null, null, null, null, null);
    }

    // 서비스가 만든 최종 검색 조건을 꺼낸다.
    private UsedProductSearchCondition capturedCondition() {
        ArgumentCaptor<UsedProductSearchCondition> captor =
                ArgumentCaptor.forClass(UsedProductSearchCondition.class);
        verify(usedProductRepository).search(captor.capture(), any());
        return captor.getValue();
    }

    private Slice<UsedProduct> emptySlice() {
        return new SliceImpl<>(List.of(), PageRequest.of(0, 20), false);
    }

    private UsedProductCreateRequestDto createRequest(UsedProductPriceType priceType, BigDecimal price) {
        UsedProductCreateRequestDto request = new UsedProductCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", CATEGORY_ID);
        ReflectionTestUtils.setField(request, "regionId", REGION_ID);
        ReflectionTestUtils.setField(request, "title", "자전거 팝니다");
        ReflectionTestUtils.setField(request, "content", "거의 새것입니다");
        ReflectionTestUtils.setField(request, "priceType", priceType);
        ReflectionTestUtils.setField(request, "price", price);
        return request;
    }

    private UsedProductUpdateRequestDto updateRequest(UsedProductPriceType priceType, BigDecimal price) {
        UsedProductUpdateRequestDto request = new UsedProductUpdateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", CATEGORY_ID);
        ReflectionTestUtils.setField(request, "title", "수정된 제목");
        ReflectionTestUtils.setField(request, "content", "수정된 본문");
        ReflectionTestUtils.setField(request, "priceType", priceType);
        ReflectionTestUtils.setField(request, "price", price);
        return request;
    }

    private UsedProduct product() {
        UsedProduct product = UsedProduct.create(
                seller(), usedCategory(), region(), "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(product, "usedProductId", PRODUCT_ID);
        return product;
    }

    private Account seller() {
        Account account = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", SELLER_ID);
        return account;
    }

    private Category usedCategory() {
        Category category = Category.createRoot(CategoryType.USED, "디지털기기", 1);
        ReflectionTestUtils.setField(category, "categoryId", CATEGORY_ID);
        return category;
    }

    private Region region() {
        Region region = Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3);
        ReflectionTestUtils.setField(region, "regionId", REGION_ID);
        return region;
    }

    private AccountRegion accountRegion(boolean verified) {
        AccountRegion accountRegion = AccountRegion.create(seller(), region());
        ReflectionTestUtils.setField(accountRegion, "accountRegionId", ACCOUNT_REGION_ID);
        ReflectionTestUtils.setField(accountRegion, "verified", verified);
        return accountRegion;
    }
}
