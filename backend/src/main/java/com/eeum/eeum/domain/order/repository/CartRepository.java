package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByAccount_AccountId(Long accountId);

    long countByStore_StoreId(Long storeId);
}