package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.application.store.dto.request.NearbyStoreSearchCondition;
import com.eeum.eeum.application.store.dto.request.StoreSearchDto;
import com.eeum.eeum.domain.store.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface StoreRepositoryCustom {

    Page<Store> searchStores(StoreSearchDto condition, Pageable pageable);

    Page<Store> searchAdminStores(String keyword, String status, Pageable pageable);

    List<Store> findNearbyStoresWithFilter(NearbyStoreSearchCondition condition);

    // 공개 노출 가능한 상점인지 — 조건은 StoreVisibilityPredicate 한 곳에서 관리한다.
    boolean isPubliclyVisible(Long storeId);

    // 공개 노출 가능한 상점 수. createdFrom이 있으면 그 이후 생성된 상점만 센다
    long countPubliclyVisible(LocalDateTime createdFrom);

    // 공개 노출 가능한 상점을 생성 최신순으로 limit개
    List<Store> findRecentPubliclyVisible(int limit);
}
