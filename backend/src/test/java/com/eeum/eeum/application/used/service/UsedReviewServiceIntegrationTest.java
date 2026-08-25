package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedReviewCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedReviewUpdateRequestDto;
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
import com.eeum.eeum.domain.used.entity.UsedReview;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.domain.used.repository.UsedReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 중고거래 후기 작성 자격과 중복 방지 검증.
 *
 * <p>자격은 "거래 완료(SOLD) + 본인이 지정 구매자"다. 이 판정이 느슨하면 거래하지 않은 사람의
 * 후기가 남고, 반대로 빡빡하면 정상 거래에 후기를 못 쓴다.
 *
 * <p>중복 방지는 존재 확인과 DB UNIQUE 두 겹이다. 존재 확인만으로는 동시 요청에 중복이 들어가므로
 * 제약이 실제로 걸려 있는지는 실제 DB로만 확인된다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedReviewServiceIntegrationTest extends IntegrationTestSupport {

    private final UsedReviewService usedReviewService;
    private final UsedProductService usedProductService;
    private final UsedReviewRepository usedReviewRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final NotificationRepository notificationRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;

    private Long sellerId;
    private Long buyerId;
    private Long strangerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        Account seller = accountRepository.save(Account.createUser(
                "r-seller-" + tag + "@test.com", "pw", "판매자", "판매자" + tag, "010-2222-2222"));
        Account buyer = accountRepository.save(Account.createUser(
                "r-buyer-" + tag + "@test.com", "pw", "구매자", "구매자" + tag, "010-1111-1111"));
        Account stranger = accountRepository.save(Account.createUser(
                "r-other-" + tag + "@test.com", "pw", "제3자", "제3자" + tag, "010-3333-3333"));
        sellerId = seller.getAccountId();
        buyerId = buyer.getAccountId();
        strangerId = stranger.getAccountId();

        Region region = regionRepository.save(Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));

        productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, "자전거 팝니다", "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
    }

    @AfterEach
    void tearDown() {
        usedReviewRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
    }

    // ─────────────────── 작성 자격 ───────────────────

    @Test
    void 지정된_구매자는_거래_완료_후_후기를_쓸_수_있다() {
        usedProductService.markSold(sellerId, productId, buyerId);

        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(5, "좋은 거래였어요"));

        assertThat(created.getRating()).isEqualTo(5);
        assertThat(created.getReviewerAccountId()).isEqualTo(buyerId);
        assertThat(created.getUsedProductId()).isEqualTo(productId);
    }

    @Test
    void 거래가_완료되지_않으면_후기를_쓸_수_없다() {
        // 예약 단계에서 후기가 열리면 거래가 성사되지 않은 채 평판이 남는다.
        usedProductService.reserve(sellerId, productId, buyerId);

        assertThatThrownBy(() -> usedReviewService.create(buyerId, productId, createRequest(5, "내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_COMPLETED);
    }

    @Test
    void 거래_상대가_아니면_후기를_쓸_수_없다() {
        usedProductService.markSold(sellerId, productId, buyerId);

        assertThatThrownBy(() -> usedReviewService.create(strangerId, productId, createRequest(5, "내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_COMPLETED);
    }

    @Test
    void 판매자는_자기_거래에_후기를_쓸_수_없다() {
        // 구매자로 지정될 수 없으므로 자격 판정에서 자연히 걸린다.
        usedProductService.markSold(sellerId, productId, buyerId);

        assertThatThrownBy(() -> usedReviewService.create(sellerId, productId, createRequest(5, "내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_COMPLETED);
    }

    @Test
    void 구매자_없이_완료된_거래에는_후기가_붙지_않는다() {
        // 앱 밖에서 성사돼 상태만 정리한 글.
        usedProductService.markSold(sellerId, productId, null);

        assertThatThrownBy(() -> usedReviewService.create(buyerId, productId, createRequest(5, "내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_COMPLETED);
    }

    @Test
    void 예약이_취소되면_후기_자격도_사라진다() {
        usedProductService.reserve(sellerId, productId, buyerId);
        usedProductService.cancelReservation(sellerId, productId);
        usedProductService.markSold(sellerId, productId, null);

        assertThatThrownBy(() -> usedReviewService.create(buyerId, productId, createRequest(5, "내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_COMPLETED);
    }

    @Test
    void 삭제된_게시글에도_거래_당사자는_후기를_쓸_수_있다() {
        // 후기 자격은 "그 거래를 실제로 했는가"이지 "게시글이 아직 살아 있는가"가 아니다.
        // 막으면 판매자가 구매자보다 먼저 글을 지워 나쁜 후기를 원천 봉쇄할 수 있다 —
        // 기존 후기를 남기는 평판 세탁 방지가 반쪽이 된다.
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);

        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(2, "약속을 안 지켰어요"));

        assertThat(created.getRating()).isEqualTo(2);
        assertThat(created.getReviewerAccountId()).isEqualTo(buyerId);
    }

    @Test
    void 삭제된_게시글이라도_거래_상대가_아니면_여전히_쓸_수_없다() {
        // 자격 기준을 거래 사실로 옮긴 것이지 자격 자체를 푼 것이 아니다.
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);

        assertThatThrownBy(() -> usedReviewService.create(strangerId, productId, createRequest(5, "내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_COMPLETED);
    }

    @Test
    void 이미_쓴_후기가_있으면_다시_쓸_수_없다() {
        usedProductService.markSold(sellerId, productId, buyerId);
        usedReviewService.create(buyerId, productId, createRequest(5, "첫 후기"));

        assertThatThrownBy(() -> usedReviewService.create(buyerId, productId, createRequest(3, "두 번째")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_ALREADY_EXISTS);

        assertThat(usedReviewRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_거래_같은_작성자는_DB가_두_번째_INSERT를_막는다() {
        // 서비스의 존재 확인이 뚫렸을 때를 가정해 제약만 따로 검증한다.
        // 제약이 빠지면(엔티티 UniqueConstraint 누락) 동시 요청에 중복 후기가 들어간다.
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        Account buyer = accountRepository.findById(buyerId).orElseThrow();
        usedReviewRepository.saveAndFlush(UsedReview.create(product, buyer, 5, "첫 후기"));

        assertThatThrownBy(() -> usedReviewRepository
                .saveAndFlush(UsedReview.create(product, buyer, 3, "두 번째")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 게시글이_삭제돼도_이미_쓴_후기는_남는다() {
        // 판매완료 글에 후기가 매달려 있다는 것이 UsedProduct를 Soft Delete로 둔 이유다.
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(5, "좋은 거래였어요"));

        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);

        assertThat(usedReviewRepository.findById(created.getUsedReviewId())).isPresent();
    }

    // ─────────────────── 수정·삭제 ───────────────────

    @Test
    void 작성자는_자기_후기를_수정할_수_있다() {
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(5, "좋아요"));

        UsedReviewResponseDto updated = usedReviewService.update(
                buyerId, created.getUsedReviewId(), updateRequest(3, "다시 보니 보통이에요"));

        // flush 없이 응답을 만들면 modifiedAt이 수정 전 값으로 남는다.
        assertThat(updated.getRating()).isEqualTo(3);
        assertThat(updated.getContent()).isEqualTo("다시 보니 보통이에요");
        assertThat(updated.getModifiedAt()).isNotNull();
    }

    @Test
    void 남의_후기는_수정할_수_없고_존재_여부도_드러나지_않는다() {
        // 남의 후기든 없는 후기든 같은 404로 응답해 ID 추측으로 존재를 알아내지 못하게 한다.
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(5, "좋아요"));

        assertThatThrownBy(() -> usedReviewService.update(
                strangerId, created.getUsedReviewId(), updateRequest(1, "조작")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_FOUND);

        assertThat(usedReviewRepository.findById(created.getUsedReviewId()).orElseThrow().getRating())
                .isEqualTo(5);
    }

    @Test
    void 작성자는_자기_후기를_삭제할_수_있다() {
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(5, "좋아요"));

        usedReviewService.delete(buyerId, created.getUsedReviewId());

        assertThat(usedReviewRepository.findById(created.getUsedReviewId())).isEmpty();
    }

    @Test
    void 남의_후기는_삭제할_수_없다() {
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(5, "좋아요"));

        assertThatThrownBy(() -> usedReviewService.delete(strangerId, created.getUsedReviewId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_FOUND);

        assertThat(usedReviewRepository.findById(created.getUsedReviewId())).isPresent();
    }

    @Test
    void 삭제한_뒤에는_다시_쓸_수_있다() {
        // 후기는 물리 삭제라 UNIQUE가 남지 않는다. 삭제 후 재작성이 막히면 사용자가 갇힌다.
        usedProductService.markSold(sellerId, productId, buyerId);
        UsedReviewResponseDto created =
                usedReviewService.create(buyerId, productId, createRequest(5, "좋아요"));
        usedReviewService.delete(buyerId, created.getUsedReviewId());

        UsedReviewResponseDto rewritten =
                usedReviewService.create(buyerId, productId, createRequest(4, "다시 씁니다"));

        assertThat(rewritten.getUsedReviewId()).isNotEqualTo(created.getUsedReviewId());
    }

    // ─────────────────── 잠금·계정 상태 ───────────────────

    @Test
    void 수정_삭제가_겹쳐도_500이_아니라_찾을_수_없음으로_끝난다() throws Exception {
        // 후기는 작성자만 고칠 수 있어 경쟁하는 두 요청의 actor가 언제나 같다.
        // 그래서 직렬화는 쓰기 경로 첫 단계인 계정 행 잠금이 하고, 뒤에 온 요청은 그 뒤에
        // current read로 사라진 행을 본다. 일반 조회였다면 flush에서 StaleStateException(500)이다.
        usedProductService.markSold(sellerId, productId, buyerId);
        Long reviewId = usedReviewService
                .create(buyerId, productId, createRequest(5, "좋아요")).getUsedReviewId();

        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        raceOnLock(transactionManager, "account",
                () -> usedReviewService.delete(buyerId, reviewId),
                () -> usedReviewService.update(buyerId, reviewId, updateRequest(1, "수정")),
                secondFailure);

        assertThat(secondFailure.get())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_NOT_FOUND);

        assertThat(usedReviewRepository.findById(reviewId)).isEmpty();
    }

    @Test
    void 탈퇴한_계정은_자기_후기를_고칠_수_없다() {
        // 다른 쓰기 경로와 같은 규약이다 — 탈퇴 정리가 지나간 뒤 살아 있는 토큰으로 들어오는 쓰기를 막는다.
        usedProductService.markSold(sellerId, productId, buyerId);
        Long reviewId = usedReviewService
                .create(buyerId, productId, createRequest(5, "좋아요")).getUsedReviewId();

        Account buyer = accountRepository.findById(buyerId).orElseThrow();
        buyer.withdraw();
        accountRepository.saveAndFlush(buyer);

        assertThatThrownBy(() -> usedReviewService.update(buyerId, reviewId, updateRequest(1, "수정")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        assertThat(usedReviewRepository.findById(reviewId).orElseThrow().getRating()).isEqualTo(5);
    }

    private UsedReviewCreateRequestDto createRequest(int rating, String content) {
        UsedReviewCreateRequestDto request = new UsedReviewCreateRequestDto();
        ReflectionTestUtils.setField(request, "rating", rating);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }

    private UsedReviewUpdateRequestDto updateRequest(int rating, String content) {
        UsedReviewUpdateRequestDto request = new UsedReviewUpdateRequestDto();
        ReflectionTestUtils.setField(request, "rating", rating);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }
}
