package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedReviewCreateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedReviewResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedReviewSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
import com.eeum.eeum.common.dto.response.CursorSlice;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final ChatRoomRepository chatRoomRepository;
    private final JdbcTemplate jdbcTemplate;

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
        chatRoomRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 판매자가_받은_후기를_최신순으로_돌려준다() {
        List<Long> reviewIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            reviewIds.add(writeReview("상품" + i, 5 - i, "후기" + i));
        }

        CursorSlice<UsedReviewResponseDto> result =
                usedReviewService.getSellerReviews(sellerId, strangerId, null, null, 20);

        // 마지막에 쓴 후기가 먼저 온다.
        assertThat(result.getContent()).extracting(UsedReviewResponseDto::getUsedReviewId)
                .containsExactly(reviewIds.get(2), reviewIds.get(1), reviewIds.get(0));

        // 응답 메타데이터도 실제 순서를 말해야 한다.
        // UNSORTED로 나가면 클라이언트는 응답만 보고 어떤 순서인지 알 수 없다.
        assertThat(result.getSort())
                .containsExactly(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("usedReviewId"));
    }

    @Test
    void 작성일시가_같은_후기도_페이지_경계에서_겹치지_않는다() {
        // 커서에 createdAt만 담으면 같은 시각에 등록된 후기들 사이에서 경계를 끊지 못해
        // 그 행이 다음 페이지에 다시 나오거나 통째로 건너뛰어진다.
        // 초 단위가 겹치는 것은 실제로 일어나므로 created_at을 같은 값으로 못박고 검증한다.
        List<Long> reviewIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            reviewIds.add(writeReview("상품" + i, 3, "후기" + i));
        }
        fixCreatedAt(reviewIds, LocalDateTime.of(2026, 9, 1, 10, 0));

        // When: 세 후기의 작성일시가 모두 같으므로 순서는 오직 ID 내림차순이다
        CursorSlice<UsedReviewResponseDto> first =
                usedReviewService.getSellerReviews(sellerId, strangerId, null, null, 2);
        CursorSlice<UsedReviewResponseDto> second = usedReviewService.getSellerReviews(
                sellerId, strangerId, first.getNextCursorValue(), first.getNextCursorId(), 2);

        // Then: id < cursorId 분기가 없으면 여기서 2페이지가 1페이지를 그대로 반복한다
        assertThat(first.getContent()).extracting(UsedReviewResponseDto::getUsedReviewId)
                .containsExactly(reviewIds.get(2), reviewIds.get(1));
        assertThat(second.getContent()).extracting(UsedReviewResponseDto::getUsedReviewId)
                .containsExactly(reviewIds.get(0));
        assertThat(second.hasNext()).isFalse();
    }

    // created_at은 @CreatedDate라 엔티티로는 고칠 수 없다. 동률 상황을 만들려면 직접 못박아야 한다.
    private void fixCreatedAt(List<Long> reviewIds, LocalDateTime createdAt) {
        for (Long reviewId : reviewIds) {
            jdbcTemplate.update(
                    "UPDATE used_review SET created_at = ? WHERE used_review_id = ?",
                    createdAt, reviewId);
        }
    }

    @Test
    void 스크롤_도중_새_후기가_등록돼도_경계가_중복되지_않는다() {
        // OFFSET 페이징이었을 때의 결함을 고정한다. 최신순 목록은 새 행이 맨 앞에 꽂히므로
        // 1페이지를 읽고 2페이지를 요청하는 사이 한 건이 등록되면 목록 전체가 한 칸 밀려,
        // 경계에 있던 후기가 2페이지에서 그대로 다시 나왔다.
        Long oldest = writeReview("상품0", 5, "후기0");
        Long middle = writeReview("상품1", 5, "후기1");
        Long newest = writeReview("상품2", 5, "후기2");

        CursorSlice<UsedReviewResponseDto> first =
                usedReviewService.getSellerReviews(sellerId, strangerId, null, null, 2);
        assertThat(first.getContent()).extracting(UsedReviewResponseDto::getUsedReviewId)
                .containsExactly(newest, middle);

        // When: 두 페이지를 읽는 사이 새 후기가 등록된다
        Long inserted = writeReviewBy(strangerId, "끼어든 상품", 4, "끼어든 후기");

        CursorSlice<UsedReviewResponseDto> second =
                usedReviewService.getSellerReviews(sellerId, strangerId, first.getNextCursorValue(), first.getNextCursorId(), 2);

        // Then: 이미 본 것은 다시 오지 않고, 남은 것만 온다.
        // OFFSET(2)이었다면 여기서 middle이 한 번 더 나온다.
        assertThat(second.getContent()).extracting(UsedReviewResponseDto::getUsedReviewId)
                .containsExactly(oldest)
                .doesNotContain(middle, newest, inserted);
    }

    @Test
    void 무한_스크롤_다음_페이지_여부를_판정한다() {
        for (int i = 0; i < 3; i++) {
            writeReview("상품" + i, 5, "후기" + i);
        }

        CursorSlice<UsedReviewResponseDto> first =
                usedReviewService.getSellerReviews(sellerId, strangerId, null, null, 2);
        CursorSlice<UsedReviewResponseDto> second =
                usedReviewService.getSellerReviews(sellerId, strangerId, first.getNextCursorValue(), first.getNextCursorId(), 2);

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

        CursorSlice<UsedReviewResponseDto> mine =
                usedReviewService.getMyReviews(buyerId, null, null, 20);

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
                .getSellerReviews(sellerId, strangerId, null, null, 20).getContent().get(0);

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
                .getSellerReviews(sellerId, strangerId, null, null, 20).getContent().get(0);

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

        CursorSlice<UsedReviewResponseDto> result =
                usedReviewService.getSellerReviews(sellerId, strangerId, null, null, 20);
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
                .getMyReviews(buyerId, null, null, 20).getContent().get(0);

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
                .getSellerReviews(sellerId, buyerId, null, null, 20).getContent().get(0);
        UsedReviewResponseDto asStranger = usedReviewService
                .getSellerReviews(sellerId, strangerId, null, null, 20).getContent().get(0);

        assertThat(asAuthor.getUsedProductTitle()).isEqualTo("자전거 팝니다");
        assertThat(asStranger.getUsedProductTitle()).isNull();
    }

    @Test
    void 판매자에게는_자기_글의_후기_제목이_보인다() {
        // 허용 예외는 "작성자·소유자 본인"이다. 작성자만 인정하면 판매자가 자기 글을 지운 뒤
        // 받은 후기 목록에서 어느 거래에 대한 후기인지 알 수 없게 된다.
        // 숨김 글은 상세 조회에서 이미 소유자에게 열려 있어(UsedProductService.getVisibleOrThrow)
        // 여기서만 막으면 같은 판매자가 경로에 따라 다른 것을 보게 된다.
        Long productId = soldProduct("자전거 팝니다");
        usedReviewService.create(buyerId, productId, request(2, "별로였어요"));
        softDelete(productId);

        UsedReviewResponseDto asSeller = usedReviewService
                .getSellerReviews(sellerId, sellerId, null, null, 20).getContent().get(0);

        assertThat(asSeller.getUsedProductTitle()).isEqualTo("자전거 팝니다");
        assertThat(asSeller.isUsedProductVisible()).isFalse();
    }

    @Test
    void 비회원에게는_비공개_게시글의_제목을_감춘다() {
        // 판매자 후기 목록은 비회원도 볼 수 있어 뷰어가 null로 들어온다.
        Long productId = soldProduct("자전거 팝니다");
        usedReviewService.create(buyerId, productId, request(2, "별로였어요"));
        softDelete(productId);

        UsedReviewResponseDto anonymous = usedReviewService
                .getSellerReviews(sellerId, null, null, null, 20).getContent().get(0);

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
        return soldProductFor(buyerId, title);
    }

    // 구매자 지정은 이 상품으로 문의한 적이 있는 상대만 가능하다 — 문의 이력을 함께 만든다.
    private Long soldProductFor(Long designatedBuyerId, String title) {
        Long productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, title, "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
        Account designatedBuyer = accountRepository.findById(designatedBuyerId).orElseThrow();
        chatRoomRepository.save(ChatRoom.createPrivateInquiry(designatedBuyer, productId));
        usedProductService.markSold(sellerId, productId, designatedBuyerId);
        return productId;
    }

    private Long writeReview(String title, int rating, String content) {
        return writeReviewBy(buyerId, title, rating, content);
    }

    private Long writeReviewBy(Long reviewerId, String title, int rating, String content) {
        Long productId = soldProductFor(reviewerId, title);
        return usedReviewService.create(reviewerId, productId, request(rating, content))
                .getUsedReviewId();
    }

    // ===================== 판매자 평판 요약 =====================

    @Test
    void 후기가_없으면_평균은_null이고_개수는_0이다() {
        // Given: 0.0으로 내리면 "별 0개"로 읽혀 후기 없는 판매자가 최악으로 보인다

        // When
        UsedReviewSummaryResponseDto summary = usedReviewService.getSellerReviewSummary(sellerId);

        // Then
        assertThat(summary.getReviewCount()).isZero();
        assertThat(summary.getAverageRating()).isNull();
    }

    @Test
    void 받은_후기의_개수와_평균_별점을_돌려준다() {
        // Given
        writeReview("상품A", 5, "좋아요");
        writeReviewBy(strangerId, "상품B", 4, "괜찮아요");

        // When
        UsedReviewSummaryResponseDto summary = usedReviewService.getSellerReviewSummary(sellerId);

        // Then
        assertThat(summary.getSellerId()).isEqualTo(sellerId);
        assertThat(summary.getReviewCount()).isEqualTo(2);
        assertThat(summary.getAverageRating()).isEqualByComparingTo("4.5");
    }

    @Test
    void 평균은_소수_1자리로_반올림한다() {
        // Given: double을 그대로 내보내면 JSON에 4.333333...이 나간다
        writeReview("상품A", 5, "좋아요");
        writeReviewBy(strangerId, "상품B", 4, "괜찮아요");
        writeReviewBy(buyerId2(), "상품C", 4, "무난해요");

        // When
        UsedReviewSummaryResponseDto summary = usedReviewService.getSellerReviewSummary(sellerId);

        // Then
        assertThat(summary.getAverageRating()).isEqualByComparingTo("4.3");
    }

    @Test
    void 삭제된_게시글의_후기도_집계에_포함한다() {
        // Given: 빼면 판매자가 나쁜 후기 달린 글을 지워 평균을 올릴 수 있다
        writeReview("좋은거래", 5, "좋아요");
        Long badProductId = soldProductFor(strangerId, "나쁜거래");
        usedReviewService.create(strangerId, badProductId, request(1, "별로예요"));

        UsedProduct bad = usedProductRepository.findById(badProductId).orElseThrow();
        bad.softDelete();
        usedProductRepository.saveAndFlush(bad);

        // When
        UsedReviewSummaryResponseDto summary = usedReviewService.getSellerReviewSummary(sellerId);

        // Then: 삭제해도 별 1점이 평균에 남아 있어야 한다
        assertThat(summary.getReviewCount()).isEqualTo(2);
        assertThat(summary.getAverageRating()).isEqualByComparingTo("3.0");
    }

    @Test
    void 다른_판매자의_후기는_섞이지_않는다() {
        // Given
        writeReview("내상품", 5, "좋아요");
        Account otherSeller = accountRepository.save(Account.createUser(
                "l-other-seller-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com",
                "pw", "판매자2", "판매자2" + UUID.randomUUID().toString().substring(0, 8),
                "010-6666-6666"));

        // When
        UsedReviewSummaryResponseDto summary =
                usedReviewService.getSellerReviewSummary(otherSeller.getAccountId());

        // Then
        assertThat(summary.getReviewCount()).isZero();
    }

    // 세 번째 후기 작성자 — 평균 반올림 검증용
    private Long buyerId2() {
        Account extra = accountRepository.save(Account.createUser(
                "l-buyer2-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com",
                "pw", "구매자2", "구매자2" + UUID.randomUUID().toString().substring(0, 8),
                "010-7777-7777"));
        return extra.getAccountId();
    }

    private UsedReviewCreateRequestDto request(int rating, String content) {
        UsedReviewCreateRequestDto dto = new UsedReviewCreateRequestDto();
        ReflectionTestUtils.setField(dto, "rating", rating);
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }
}
