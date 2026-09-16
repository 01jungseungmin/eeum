package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCart_CartId(Long cartId);

    void deleteByCart_CartId(Long cartId);

    Optional<CartItem> findByCart_CartIdAndProduct_ProductIdAndSelectedOptionsHash(
            Long cartId,
            Long productId,
            String selectedOptionsHash
    );

    Optional<CartItem> findByCart_CartIdAndEventProduct_EventProductId(
            Long cartId,
            Long eventProductId
    );
}