package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.SettlementAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, Long> {

    Optional<SettlementAccount> findByStore_StoreId(Long storeId);

    boolean existsByStore_StoreId(Long storeId);
    void deleteByStore_StoreId(Long storeId);
}