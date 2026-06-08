package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.entity.Order;
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "rating", nullable = false)
    private int rating; // 1 ~ 5

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public static StoreReview create(
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