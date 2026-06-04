package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long>,StoreRepositoryCustom {

    boolean existsByAccount_AccountId(Long accountId);

    Optional<Store> findByAccount_AccountId(Long accountId);

    void deleteByAccount_AccountId(Long accountId);
}