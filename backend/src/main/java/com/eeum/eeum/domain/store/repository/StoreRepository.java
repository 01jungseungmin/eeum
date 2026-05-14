package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store,Long> {
}
