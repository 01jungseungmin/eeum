package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.response.FavoriteCustomerResponseDto;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.CustomerOrderStatProjection;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.CustomerReviewStatProjection;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerFavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final StoreReviewRepository storeReviewRepository;

    // 사장용 찜 고객 목록 조회 Favorite 1번 + Order 집계 1번 + Review 집계 1번 = 총 3 쿼리로 N+1 없이 처리
    //@param ownerId  요청 사장 accountId
    // @param pageable 페이지 정보 (기본 최신순)
    @Transactional(readOnly = true)
    public Page<FavoriteCustomerResponseDto> getFavoriteCustomers(
            Long ownerId, Pageable pageable
    ) {
        Long storeId = getOwnerStoreId(ownerId);

        // ① Favorite + Account JOIN FETCH (1 쿼리)
        Page<Favorite> favorites = favoriteRepository
                .findByRefTypeAndRefIdWithAccount(FavoriteRefType.STORE, storeId, pageable);

        // 빈 페이지이면 추가 쿼리 없이 반환
        List<Long> accountIds = favorites.getContent().stream()
                .map(f -> f.getAccount().getAccountId())
                .collect(Collectors.toList());

        if (accountIds.isEmpty()) {
            return favorites.map(fav ->
                    FavoriteCustomerResponseDto.of(fav, fav.getAccount(), null, null));
        }

        // ② 완료 주문 통계 배치 조회 (1 쿼리)
        Map<Long, CustomerOrderStatProjection> orderStatMap = orderRepository
                .findOrderStatsByStoreAndAccounts(storeId, accountIds, OrderStatus.COMPLETED)
                .stream()
                .collect(Collectors.toMap(CustomerOrderStatProjection::getAccountId, Function.identity()));

        // ③ 리뷰 평점 배치 조회 (1 쿼리)
        Map<Long, CustomerReviewStatProjection> reviewStatMap = storeReviewRepository
                .findReviewStatsByStoreAndAccounts(storeId, accountIds)
                .stream()
                .collect(Collectors.toMap(CustomerReviewStatProjection::getAccountId, Function.identity()));

        return favorites.map(fav -> FavoriteCustomerResponseDto.of(
                fav,
                fav.getAccount(),
                orderStatMap.get(fav.getAccount().getAccountId()),
                reviewStatMap.get(fav.getAccount().getAccountId())
        ));
    }

    // 사장 accountId로 내 상점 ID 조회 — /owner/stores/me 패턴 공통 유틸
    private Long getOwnerStoreId(Long ownerId) {
        return storeRepository.findByAccount_AccountId(ownerId)
                .map(Store::getStoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
