package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cart")
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    public static Cart create(Account account) {
        Cart cart = new Cart();
        cart.account = account;
        cart.store = null;
        return cart;
    }

    public void updateStore(Store store) {
        this.store = store;
    }

    public void clear() {
        this.store = null;
    }

    public boolean isSameStore(Long storeId) {
        return this.store != null && this.store.getStoreId().equals(storeId);
    }

    public boolean isEmpty() {
        return this.store == null;
    }
}