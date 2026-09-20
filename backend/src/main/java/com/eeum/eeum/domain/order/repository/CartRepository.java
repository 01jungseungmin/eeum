package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByAccount_AccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.account.accountId = :accountId")
    Optional<Cart> findByAccountIdWithPessimisticLock(@Param("accountId") Long accountId);

    long countByStore_StoreId(Long storeId);

    List<Cart> findByStore_StoreId(Long storeId);
}
