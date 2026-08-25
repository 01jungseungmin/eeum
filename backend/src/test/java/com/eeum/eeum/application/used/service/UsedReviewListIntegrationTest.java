package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedReviewCreateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedReviewResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.domain.used.repository.UsedReviewRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.support.SqlCaptureInspector;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 후기 목록 조회 검증.
 *
 * <p>응답이 게시글 제목과 작성자 닉네임을 담으므로 fetch join이 빠지면 페이지 크기만큼
 * 추가 select가 나간다. 실행 쿼리 수는 실제 DB로만 확인된다.
 *
 * <p>비공개 게시글의 후기를 어떻게 다루는지도 함께 고정한다 — 후기는 남기되 제목은 감춘다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedReviewListIntegrationTest extends IntegrationTestSupport {

    private final UsedReviewService usedReviewService;
    private final UsedProductService usedProductService;
    private final UsedReviewRepository usedReviewRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final NotificationRepository notificationRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long sellerId;
    private Long buyerId;
    private Long strangerId;
    private Account seller;
    private Region region;
    private Category category;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        seller = accountRepository.save(Account.createUser(
                "l-seller-" + tag + "@test.com", "pw", "판매자", "판매자" + tag, "010-2222-2222"));
        Account buyer = accountRepository.save(Account.createUser(
                "l-buyer-" + tag + "@test.com", "pw", "구매자", "구매자" + tag, "010-1111-1111"));
        Account stranger = accountRepository.save(Account.createUser(
                "l-stranger-" + tag + "@test.com", "pw", "제3자", "제3자" + tag, "010-5555-5555"));
        sellerId = seller.getAccountId();
        buyerId = buyer.getAccountId();
        strangerId = stranger.getAccountId();

        region = regionRepository.save(Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        category = categoryRepository.save(Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));
    }

    @AfterEach
    void tearDown() {
        usedReviewRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        // 판매완료가 AFTER_COMMIT + @Async로 후기 요청 알림을 남긴다.
        deleteAccountsAbsorbingAsyncNotifications(notificationRepository, accountRepository);
    }

    @Test
    void 판매자가_받은_후기를_최신순으로_돌려준다() {
        List<Long> reviewIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            reviewIds.add(writeReview("상품" + i, 5 - i, "후기" + i));
        }

        Slice<UsedReviewResponseDto> result =
                usedReviewService.getSellerReviews(sellerId, strangerId, PageRequest.of(0, 20));

        // 마지막에 쓴 후기가 먼저 온다.
        assertThat(result.getContent()).extracting(UsedReviewResponseDto::getUsedReviewId)
                .containsExactly(reviewIds.get(2), reviewIds.get(1), reviewIds.get(0));
    }

    @Test
    void 요청_정렬과_무관하게_작성_최신순으로_고정한다() {
        // 요청 sort를 그대로 넘기면 쿼리의 ORDER BY 뒤에 덧붙어 실제 순서가 달라진다.
        List<Long> reviewIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            reviewIds.add(writeReview("상품" + i, 3, "후기" + i));
        }

        Slice<UsedReviewResponseDto> result = usedReviewService.getSellerReviews(
                sellerId, strangerId, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "rating")));

        assertThat(result.getContent()).extracting(UsedReviewResponseDto::getUsedReviewId)
                .containsExactly(reviewIds.get(2), reviewIds.get(1), reviewIds.get(0));
    }

    @Test
    void 무한_스크롤_다음_페이지_여부를_판정한다() {
        for (int i = 0; i < 3; i++) {
            writeReview("상품" + i, 5, "후기" + i);
        }

        Slice<UsedReviewResponseDto> first =
                usedReviewService.getSellerReviews(sellerId, strangerId, PageRequest.of(0, 2));
        Slice<UsedReviewResponseDto> second =
                usedReviewService.getSellerReviews(sellerId, strangerId, PageRequest.of(1, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 내가_쓴_후기만_돌려준다() {
        writeReview("내가 산 물건", 5, "좋았어요");

        String tag = UUID.randomUUID().toString().substring(0, 8);
        Account otherBuyer = accountRepository.save(Account.createUser(
                "l-other-" + tag + "@test.com", "pw", "다른구매자", "다른구매자" + tag, "010-4444-4444"));
        writeReviewBy(otherBuyer.getAccountId(), "남이 산 물건", 4, "괜찮아요");

        Slice<UsedReviewResponseDto> mine =
                usedReviewService.getMyReviews(buyerId, PageRequest.of(0, 20));

        assertThat(mine.getContent()).hasSize(1);
        assertThat(mine.getContent().get(0).getContent()).isEqualTo("좋았어요");
    }

    @Test
    void 게시글이_삭제돼도_후기는_남고_제목만_가려진다() {
        // 거르면 판매자가 나쁜 후기가 달린 글을 지워 평판을 세탁할 수 있다.
        Long productId = soldProduct("자전거 팝니다");
        usedReviewService.create(buyerId, productId, request(2, "별로였어요"));

        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);

        UsedReviewResponseDto review = usedReviewService
                .getSellerReviews(sellerId, strangerId, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(review.getContent()).isEqualTo("별로였어요");
        assertThat(review.isUsedProductVisible()).isFalse();
        assertThat(review.getUsedProductTitle())
                .as("삭제된 게시글의 제목이 후기 목록으로 새어 나가면 안 된다")
                .isNull();
    }

    @Test
    void 관리자가_숨긴_게시글도_같은_방식으로_제목만_가린다() {
        Long productId = soldProduct("신고된 물건");
        usedReviewService.create(buyerId, productId, request(1, "문제가 있었어요"));

        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.hide();
        usedProductRepository.saveAndFlush(product);

        UsedReviewResponseDto review = usedReviewService
                .getSellerReviews(sellerId, strangerId, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(review.isUsedProductVisible()).isFalse();
        assertThat(review.getUsedProductTitle()).isNull();
        assertThat(review.getUsedProductId()).isEqualTo(productId);
    }

    @Test
    void 후기가_여러_건이어도_게시글과_작성자를_추가_조회하지_않는다() {
        // fetch join이 빠지면 페이지 크기만큼 select가 더 나간다(N+1).
        for (int i = 0; i < 3; i++) {
            writeReview("상품" + i, 5, "후기" + i);
        }
        SqlCaptureInspector.reset();

        Slice<UsedReviewResponseDto> result =
                usedReviewService.getSellerReviews(sellerId, strangerId, PageRequest.of(0, 20));
        result.getContent().forEach(UsedReviewResponseDto::getUsedProductTitle);

        assertThat(selectCount()).as("실행된 select: %s", SqlCaptureInspector.captured()).isEqualTo(1);
    }

    @Test
    void 작성자에게는_삭제된_게시글의_제목도_보인다() {
        // 제목을 감추는 목적은 비공개 글의 내용이 제3자에게 새어 나가는 것을 막는 것이다.
        // 작성자는 그 글을 보고 후기를 쓴 사람이라 감출 것이 없고, 감추면 판매자가 글을 지운 뒤
        // 자기 후기가 어느 거래에 대한 것인지 알 수 없게 된다.
        Long productId = soldProduct("자전거 팝니다");
        usedReviewService.create(buyerId, productId, request(2, "별로였어요"));
        softDelete(productId);

        UsedReviewResponseDto mine = usedReviewService
                .getMyReviews(buyerId, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(mine.getUsedProductTitle()).isEqualTo("자전거 팝니다");
        assertThat(mine.isUsedProductVisible())
                .as("제목을 보여주더라도 게시글이 비공개라는 사실은 그대로 알려야 상세 이동을 막을 수 있다")
                .isFalse();
    }

    @Test
    void 판매자_목록에서도_자기가_쓴_후기의_제목은_보인다() {
        Long productId = soldProduct("자전거 팝니다");
        usedReviewService.create(buyerId, productId, request(2, "별로였어요"));
        softDelete(productId);

        UsedReviewResponseDto asAuthor = usedReviewService
                .getSellerReviews(sellerId, buyerId, PageRequest.of(0, 20)).getContent().get(0);
        UsedReviewResponseDto asStranger = usedReviewService
                .getSellerReviews(sellerId, strangerId, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(asAuthor.getUsedProductTitle()).isEqualTo("자전거 팝니다");
        assertThat(asStranger.getUsedProductTitle()).isNull();
    }

    @Test
    void 비회원에게는_비공개_게시글의_제목을_감춘다() {
        // 판매자 후기 목록은 비회원도 볼 수 있어 뷰어가 null로 들어온다.
        Long productId = soldProduct("자전거 팝니다");
        usedReviewService.create(buyerId, productId, request(2, "별로였어요"));
        softDelete(productId);

        UsedReviewResponseDto anonymous = usedReviewService
                .getSellerReviews(sellerId, null, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(anonymous.getUsedProductTitle()).isNull();
        assertThat(anonymous.getContent()).isEqualTo("별로였어요");
    }

    private void softDelete(Long usedProductId) {
        UsedProduct product = usedProductRepository.findById(usedProductId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);
    }

    private long selectCount() {
        return SqlCaptureInspector.captured().stream()
                .map(String::toLowerCase)
                .filter(sql -> sql.startsWith("select"))
                .count();
    }

    // 판매완료 + 구매자 지정까지 끝난 게시글
    private Long soldProduct(String title) {
        Long productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, title, "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
        usedProductService.markSold(sellerId, productId, buyerId);
        return productId;
    }

    private Long writeReview(String title, int rating, String content) {
        return writeReviewBy(buyerId, title, rating, content);
    }

    private Long writeReviewBy(Long reviewerId, String title, int rating, String content) {
        Long productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, title, "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
        usedProductService.markSold(sellerId, productId, reviewerId);
        return usedReviewService.create(reviewerId, productId, request(rating, content))
                .getUsedReviewId();
    }

    private UsedReviewCreateRequestDto request(int rating, String content) {
        UsedReviewCreateRequestDto dto = new UsedReviewCreateRequestDto();
        ReflectionTestUtils.setField(dto, "rating", rating);
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }
}
