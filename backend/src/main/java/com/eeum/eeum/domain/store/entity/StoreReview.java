package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.store.enums.StoreReviewType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "store_review")
public class StoreReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_review_id")
    private Long storereviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // 주문 기반 리뷰일 때만 값이 있음 (reviewType = ORDER)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    // 방문 예약 기반 리뷰일 때만 값이 있음 (reviewType = RESERVATION)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_reservation_id", unique = true)
    private VisitReservation visitReservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 20)
    private StoreReviewType reviewType;

    @Column(name = "rating", nullable = false)
    private int rating; // 1 ~ 5

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public static StoreReview createForOrder(
            Store store,
            Account account,
            Order order,
            int rating,
            String content
    ) {
        StoreReview review = new StoreReview();
        review.store = store;
        review.account = account;
        review.order = order;
        review.reviewType = StoreReviewType.ORDER;
        review.rating = rating;
        review.content = content;
        return review;
    }

    public static StoreReview createForReservation(
            Store store,
            Account account,
            VisitReservation visitReservation,
            int rating,
            String content
    ) {
        StoreReview review = new StoreReview();
        review.store = store;
        review.account = account;
        review.visitReservation = visitReservation;
        review.reviewType = StoreReviewType.RESERVATION;
        review.rating = rating;
        review.content = content;
        return review;
    }

    public void update(int rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }
}