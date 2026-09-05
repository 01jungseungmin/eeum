package com.eeum.eeum.application.used.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.event.UsedProductReservationCancelledEvent;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 판매자 정지·탈퇴 시 예약 정리.
 *
 * <p>판매자가 비활성이 되면 {@code isPubliclyVisible()}이 거짓이라 게시글이 전 화면에서
 * 사라진다. 예약을 그대로 두면 구매자는 볼 수도 없는 글을 통보도 없이 기다리게 된다 —
 * 이 서비스가 막으려는 것이 그 상황이므로, 되돌림과 통보 양쪽 모두 회귀 방어가 필요하다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsedProductWithdrawalServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;

    @Mock private UsedProductRepository usedProductRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private UsedProductWithdrawalService usedProductWithdrawalService;

    private Account seller;
    private Account buyer;
    private Category category;
    private Region region;

    @BeforeEach
    void setUp() {
        seller = mock(Account.class);
        when(seller.getAccountId()).thenReturn(SELLER_ID);
        buyer = mock(Account.class);
        when(buyer.getAccountId()).thenReturn(BUYER_ID);
        category = mock(Category.class);
        when(category.getType()).thenReturn(CategoryType.USED);
        region = mock(Region.class);
    }

    @Test
    void 예약을_판매중으로_되돌리고_구매자에게_알린다() {
        // Given
        UsedProduct product = givenReserved(10L, "자전거", buyer);

        // When
        usedProductWithdrawalService.cancelReservationsForSellerInactivation(SELLER_ID);

        // Then: 지정 상대도 함께 비운다 — 남겨두면 취소된 거래의 상대가 후기 자격을 갖는다
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SELLING);
        assertThat(product.getBuyer()).isNull();

        UsedProductReservationCancelledEvent event = capturedEvent();
        assertThat(event.usedProductId()).isEqualTo(10L);
        assertThat(event.buyerAccountId()).isEqualTo(BUYER_ID);
        // 제목은 발행 트랜잭션 안에서 담는다 — 리스너는 커밋 후 비동기라 다시 읽으면 LAZY가 터진다
        assertThat(event.productTitle()).isEqualTo("자전거");
    }

    @Test
    void 상대가_없는_예약은_되돌리되_통보하지_않는다() {
        // Given: 상대 없이 "예약중" 표시만 걸어둔 글 — 통보할 사람이 없다
        UsedProduct product = givenReserved(10L, "자전거", null);

        // When
        usedProductWithdrawalService.cancelReservationsForSellerInactivation(SELLER_ID);

        // Then: 판매자 복귀 시 정상 상태가 되도록 되돌리기는 한다
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SELLING);
        verify(eventPublisher, never()).publishEvent(any(UsedProductReservationCancelledEvent.class));
    }

    @Test
    void 잠근_뒤_삭제된_글은_건너뛴다() {
        // Given: 목록 조회와 잠금 사이에 신고 조치(DELETE_POST)가 글을 지울 수 있다.
        // 그대로 취소하면 사라진 글에 대해 "예약이 취소되었습니다" 알림이 나간다.
        UsedProduct product = givenReserved(10L, "자전거", buyer);
        product.softDelete();

        // When
        usedProductWithdrawalService.cancelReservationsForSellerInactivation(SELLER_ID);

        // Then
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.RESERVED);
        verify(eventPublisher, never()).publishEvent(any(UsedProductReservationCancelledEvent.class));
    }

    @Test
    void 잠근_뒤_상태가_바뀐_글은_건너뛴다() {
        // Given: 이미 판매완료로 넘어간 글에 cancelReservation을 걸면 예외로 제재 트랜잭션이 통째로 깨진다
        UsedProduct product = givenReserved(10L, "자전거", buyer);
        product.markSold(buyer);

        // When
        usedProductWithdrawalService.cancelReservationsForSellerInactivation(SELLER_ID);

        // Then
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SOLD);
        verify(eventPublisher, never()).publishEvent(any(UsedProductReservationCancelledEvent.class));
    }

    @Test
    void 잠그는_사이_사라진_행은_건너뛴다() {
        // Given
        when(usedProductRepository.findReservedProductIdsBySeller(SELLER_ID, UsedProductStatus.RESERVED))
                .thenReturn(List.of(10L));
        when(usedProductRepository.findByUsedProductIdForUpdate(10L)).thenReturn(Optional.empty());

        // When & Then: NPE 없이 넘어가야 한다
        usedProductWithdrawalService.cancelReservationsForSellerInactivation(SELLER_ID);

        verify(eventPublisher, never()).publishEvent(any(UsedProductReservationCancelledEvent.class));
    }

    @Test
    void 목록은_엔티티가_아니라_ID로_읽고_읽은_순서대로_잠근다() {
        // Given: 엔티티로 읽으면 영속성 컨텍스트에 올라가 잠금 조회가 1차 캐시의 낡은 인스턴스를
        // 돌려준다 — "잠근 뒤 재확인"이 성립하지 않는다. 잠금 순서는 신고 조치와 맞물릴 때
        // 교착이 나지 않도록 ID 오름차순 하나로 고정한다.
        UsedProduct first = product(10L, "자전거", buyer);
        UsedProduct second = product(20L, "책상", buyer);
        when(usedProductRepository.findReservedProductIdsBySeller(SELLER_ID, UsedProductStatus.RESERVED))
                .thenReturn(List.of(10L, 20L));
        when(usedProductRepository.findByUsedProductIdForUpdate(10L)).thenReturn(Optional.of(first));
        when(usedProductRepository.findByUsedProductIdForUpdate(20L)).thenReturn(Optional.of(second));

        // When
        usedProductWithdrawalService.cancelReservationsForSellerInactivation(SELLER_ID);

        // Then
        InOrder inOrder = inOrder(usedProductRepository);
        inOrder.verify(usedProductRepository)
                .findReservedProductIdsBySeller(SELLER_ID, UsedProductStatus.RESERVED);
        inOrder.verify(usedProductRepository).findByUsedProductIdForUpdate(10L);
        inOrder.verify(usedProductRepository).findByUsedProductIdForUpdate(20L);
        assertThat(first.getStatus()).isEqualTo(UsedProductStatus.SELLING);
        assertThat(second.getStatus()).isEqualTo(UsedProductStatus.SELLING);
    }

    private UsedProduct givenReserved(Long usedProductId, String title, Account reservedBuyer) {
        UsedProduct product = product(usedProductId, title, reservedBuyer);
        when(usedProductRepository.findReservedProductIdsBySeller(SELLER_ID, UsedProductStatus.RESERVED))
                .thenReturn(List.of(usedProductId));
        when(usedProductRepository.findByUsedProductIdForUpdate(usedProductId))
                .thenReturn(Optional.of(product));
        return product;
    }

    private UsedProduct product(Long usedProductId, String title, Account reservedBuyer) {
        UsedProduct product = UsedProduct.create(
                seller, category, region, title, "내용",
                UsedProductPriceType.FIXED, BigDecimal.valueOf(10_000));
        ReflectionTestUtils.setField(product, "usedProductId", usedProductId);
        product.reserve(reservedBuyer);
        return product;
    }

    private UsedProductReservationCancelledEvent capturedEvent() {
        ArgumentCaptor<UsedProductReservationCancelledEvent> captor =
                ArgumentCaptor.forClass(UsedProductReservationCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
