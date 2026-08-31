package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.event.UsedProductSoldEvent;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 거래 상태 전이와 구매자 지정 검증.
 *
 * <p>구매자는 후기 작성 자격의 근거다(4단계 조각 2). 여기서 잘못 지정되면 남의 거래에 후기가
 * 붙거나, 정상 거래에 후기를 못 쓰게 된다.
 *
 * <p>응답이 변경 후 값을 담는지는 flush 시점에 달려 있어 실제 DB로만 확인된다.
 */
@EnabledIfDockerAvailable
@RecordApplicationEvents
@RequiredArgsConstructor
class UsedProductTradeStatusIntegrationTest extends IntegrationTestSupport {

    private final UsedProductService usedProductService;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final NotificationRepository notificationRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final ChatRoomRepository chatRoomRepository;

    private Long sellerId;
    private Long buyerId;
    private Long strangerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        Account seller = accountRepository.save(Account.createUser(
                "t-seller-" + tag + "@test.com", "pw", "판매자", "판매자" + tag, "010-2222-2222"));
        Account buyer = accountRepository.save(Account.createUser(
                "t-buyer-" + tag + "@test.com", "pw", "구매자", "구매자" + tag, "010-1111-1111"));
        Account stranger = accountRepository.save(Account.createUser(
                "t-other-" + tag + "@test.com", "pw", "제3자", "제3자" + tag, "010-3333-3333"));
        sellerId = seller.getAccountId();
        buyerId = buyer.getAccountId();
        strangerId = stranger.getAccountId();

        Region region = regionRepository.save(Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));

        productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, "자전거 팝니다", "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();

        // 구매자로 지정하려면 이 상품으로 문의한 이력이 있어야 한다
        chatRoomRepository.save(ChatRoom.createPrivateInquiry(buyer, productId));
    }

    @AfterEach
    void tearDown() {
        chatRoomRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 예약_응답은_변경_후_상태를_담는다() {
        // flush 없이 응답을 만들면 변경 전 값이 담긴다.
        UsedProductDetailResponseDto response =
                usedProductService.reserve(sellerId, productId, buyerId);

        assertThat(response.getStatus()).isEqualTo(UsedProductStatus.RESERVED);
        assertThat(product().getBuyer().getAccountId()).isEqualTo(buyerId);
    }

    @Test
    void 예약_취소는_구매자까지_비운다() {
        usedProductService.reserve(sellerId, productId, buyerId);

        usedProductService.cancelReservation(sellerId, productId);

        assertThat(product().getStatus()).isEqualTo(UsedProductStatus.SELLING);
        assertThat(product().getBuyer()).isNull();
    }

    @Test
    void 판매완료_시_구매자를_생략하면_예약_상대가_유지된다() {
        usedProductService.reserve(sellerId, productId, buyerId);

        usedProductService.markSold(sellerId, productId, null);

        assertThat(product().isPurchasedBy(buyerId)).isTrue();
    }

    @Test
    void 예약_없이_바로_판매완료할_수_있다() {
        usedProductService.markSold(sellerId, productId, buyerId);

        assertThat(product().getStatus()).isEqualTo(UsedProductStatus.SOLD);
        assertThat(product().isPurchasedBy(buyerId)).isTrue();
    }

    @Test
    void 구매자_없이_판매완료하면_후기_자격이_생기지_않는다() {
        // 앱 밖에서 성사된 거래. 상태만 정리하고 후기는 붙지 않는다.
        usedProductService.markSold(sellerId, productId, null);

        assertThat(product().getStatus()).isEqualTo(UsedProductStatus.SOLD);
        assertThat(product().getBuyer()).isNull();
    }

    @Test
    void 판매자가_아니면_상태를_바꿀_수_없다() {
        assertThatThrownBy(() -> usedProductService.reserve(strangerId, productId, buyerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_ACCESS_DENIED);

        assertThat(product().getStatus()).isEqualTo(UsedProductStatus.SELLING);
    }

    @Test
    void 본인을_구매자로_지정할_수_없다() {
        assertThatThrownBy(() -> usedProductService.markSold(sellerId, productId, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_BUYER);

        assertThat(product().getStatus()).isEqualTo(UsedProductStatus.SELLING);
    }

    @Test
    void 탈퇴한_계정은_구매자로_지정할_수_없다() {
        // 존재만 보면 탈퇴 계정이 buyer로 확정되고 후기 요청 알림·푸시가 그 계정으로 나간다.
        Account buyer = accountRepository.findById(buyerId).orElseThrow();
        buyer.withdraw();
        accountRepository.saveAndFlush(buyer);

        assertThatThrownBy(() -> usedProductService.markSold(sellerId, productId, buyerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_BUYER);

        assertThat(product().getStatus()).isEqualTo(UsedProductStatus.SELLING);
    }

    @Test
    void 없는_계정을_구매자로_지정할_수_없다() {
        assertThatThrownBy(() -> usedProductService.markSold(sellerId, productId, 999999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 삭제된_게시글은_상태를_바꿀_수_없다() {
        UsedProduct target = product();
        target.softDelete();
        usedProductRepository.saveAndFlush(target);

        assertThatThrownBy(() -> usedProductService.reserve(sellerId, productId, buyerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 이미_판매완료된_글은_다시_예약할_수_없다() {
        usedProductService.markSold(sellerId, productId, buyerId);

        assertThatThrownBy(() -> usedProductService.reserve(sellerId, productId, buyerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
    }

    // ─────────────────── 후기 요청 알림 발행 ───────────────────

    @Test
    void 구매자가_지정된_판매완료는_후기_요청_이벤트를_발행한다(ApplicationEvents events) {
        usedProductService.markSold(sellerId, productId, buyerId);

        assertThat(events.stream(UsedProductSoldEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.buyerAccountId()).isEqualTo(buyerId);
                    assertThat(event.usedProductId()).isEqualTo(productId);
                    assertThat(event.productTitle()).isEqualTo("자전거 팝니다");
                });
    }

    @Test
    void 구매자가_없으면_후기_요청_이벤트를_발행하지_않는다(ApplicationEvents events) {
        // 후기를 쓸 사람이 없는 거래다. 알림만 가면 받는 사람이 없거나 엉뚱한 사람에게 간다.
        usedProductService.markSold(sellerId, productId, null);

        assertThat(events.stream(UsedProductSoldEvent.class)).isEmpty();
    }

    @Test
    void 예약_때_지정한_구매자에게_후기_요청이_간다(ApplicationEvents events) {
        // markSold에서 buyerId를 생략해도 예약 상대가 유지되므로, 그 상대가 수신자여야 한다.
        usedProductService.reserve(sellerId, productId, buyerId);
        events.clear();

        usedProductService.markSold(sellerId, productId, null);

        assertThat(events.stream(UsedProductSoldEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.buyerAccountId()).isEqualTo(buyerId));
    }

    @Test
    void 예약과_예약취소는_후기_요청_이벤트를_발행하지_않는다(ApplicationEvents events) {
        usedProductService.reserve(sellerId, productId, buyerId);
        usedProductService.cancelReservation(sellerId, productId);

        assertThat(events.stream(UsedProductSoldEvent.class)).isEmpty();
    }

    private UsedProduct product() {
        return usedProductRepository.findById(productId).orElseThrow();
    }

    // ===================== 구매자 지정 자격 =====================

    @Test
    void 문의한_적_없는_계정은_구매자로_지정할_수_없다() {
        // Given: strangerId는 이 상품으로 문의한 이력이 없다.
        //        존재 여부만 봤을 때는 판매자가 아무 계정이나 세워 후기 권한과 알림을 줄 수 있었다.

        // When & Then
        assertThatThrownBy(() -> usedProductService.markSold(sellerId, productId, strangerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_BUYER);

        assertThat(usedProductRepository.findById(productId).orElseThrow().getStatus())
                .isEqualTo(UsedProductStatus.SELLING);
    }

    @Test
    void 문의한_적_없는_계정은_예약_상대로도_지정할_수_없다() {
        // When & Then
        assertThatThrownBy(() -> usedProductService.reserve(sellerId, productId, strangerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_BUYER);
    }

    @Test
    void 문의_후_방을_나가도_구매자로_지정할_수_있다() {
        // Given: PRIVATE 문의방은 한쪽이 나가면 종료된다.
        //        활성 방만 세면 거래를 마치고 방을 나간 상대를 지정할 수 없게 된다.
        ChatRoom room = chatRoomRepository
                .findFirstByRefTypeAndRefIdAndBuyerAccountIdAndIsActiveTrueOrderByChatroomIdDesc(
                        ChatRoomRefType.USED_PRODUCT, productId, buyerId)
                .orElseThrow();
        room.deactivate();
        chatRoomRepository.saveAndFlush(room);

        // When
        usedProductService.markSold(sellerId, productId, buyerId);

        // Then
        assertThat(usedProductRepository.findById(productId).orElseThrow().getStatus())
                .isEqualTo(UsedProductStatus.SOLD);
    }

    // ===================== 생략한 구매자도 검증한다 =====================

    @Test
    void 예약_후_구매자가_탈퇴하면_구매자를_생략한_판매완료가_막힌다() {
        // Given: 예약 시점에는 유효했지만 그 뒤 탈퇴했다.
        //        생략한 구매자를 검증 없이 확정하면 비활성 계정이 거래 구매자이자
        //        후기 자격자로 남고 판매완료 알림까지 나간다.
        usedProductService.reserve(sellerId, productId, buyerId);
        Account buyer = accountRepository.findById(buyerId).orElseThrow();
        buyer.withdraw();
        accountRepository.saveAndFlush(buyer);

        // When & Then
        assertThatThrownBy(() -> usedProductService.markSold(sellerId, productId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_BUYER);

        assertThat(usedProductRepository.findById(productId).orElseThrow().getStatus())
                .isEqualTo(UsedProductStatus.RESERVED);
    }

    @Test
    void 예약_상대가_유효하면_구매자를_생략해도_그대로_확정된다() {
        // Given
        usedProductService.reserve(sellerId, productId, buyerId);

        // When
        usedProductService.markSold(sellerId, productId, null);

        // Then
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SOLD);
        assertThat(product.getBuyer().getAccountId()).isEqualTo(buyerId);
    }

    @Test
    void 예약_상대가_없으면_구매자_없이_판매완료할_수_있다() {
        // Given: 앱 밖에서 성사된 거래. 후기는 붙지 않는다.

        // When
        usedProductService.markSold(sellerId, productId, null);

        // Then
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SOLD);
        assertThat(product.getBuyer()).isNull();
    }

    @Test
    void 예약_취소는_구매자가_탈퇴했어도_가능하다() {
        // Given: 취소는 구매자를 비우는 전이라 확정할 상대가 없다.
        //        여기까지 구매자 검증을 걸면 탈퇴한 상대로 예약된 글을 영영 못 푼다.
        usedProductService.reserve(sellerId, productId, buyerId);
        Account buyer = accountRepository.findById(buyerId).orElseThrow();
        buyer.withdraw();
        accountRepository.saveAndFlush(buyer);

        // When
        usedProductService.cancelReservation(sellerId, productId);

        // Then
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SELLING);
        assertThat(product.getBuyer()).isNull();
    }
}
