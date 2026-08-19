package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.response.FavoriteStatResponseDto;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteStatProjection;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoreRepository storeRepository;
    private final UsedProductRepository usedProductRepository;

    // 인기 항목 통계 — 기간 + refType + 상위 N개 refType별로 이름을 조회해 함께 반환

    @Transactional(readOnly = true)
    public List<FavoriteStatResponseDto> getFavoriteStats(
            FavoriteRefType refType,
            LocalDateTime from,
            LocalDateTime to,
            int limit
    ) {
        List<FavoriteStatProjection> projections =
                favoriteRepository.findFavoriteStats(refType, from, to, limit);

        // 항목마다 이름을 조회하면 상위 N개만큼 쿼리가 나가므로 ID를 모아 IN 절로 한 번에 조회한다.
        Map<Long, String> refNames = resolveRefNames(
                refType,
                projections.stream().map(FavoriteStatProjection::getRefId).toList());

        return projections.stream()
                .map(p -> FavoriteStatResponseDto.of(
                        refType, p.getRefId(), p.getFavoriteCount(),
                        refNames.getOrDefault(p.getRefId(), deletedRefName(refType))))
                .collect(Collectors.toList());
    }

    // Store.favoriteCount 정합성 재계산 장애·대량 삭제·수동 DB 수정 후 favorite 테이블 실제 수와 Store 컬럼이 어긋났을 때 사용
    // 단일 UPDATE ... SELECT로 처리 — N번 쿼리 없이 전체 동기화.
    @Transactional
    public int recalculateFavoriteCounts() {
        int updated = storeRepository.recalculateAllFavoriteCounts();
        log.info("Store.favoriteCount 정합성 재계산 완료: updatedRows={}", updated);
        return updated;
    }

    // 통계 대상 이름 배치 조회 — 찜만 남고 대상이 삭제된 refId는 map에 담기지 않는다.
    private Map<Long, String> resolveRefNames(FavoriteRefType refType, List<Long> refIds) {
        if (refIds.isEmpty()) {
            return Map.of();
        }
        return switch (refType) {
            case STORE -> storeRepository.findAllById(refIds).stream()
                    .collect(Collectors.toMap(Store::getStoreId, Store::getName));
            case USED_PRODUCT -> usedProductRepository.findAllById(refIds).stream()
                    .collect(Collectors.toMap(UsedProduct::getUsedProductId, UsedProduct::getTitle));
        };
    }

    private String deletedRefName(FavoriteRefType refType) {
        return switch (refType) {
            case STORE -> "(삭제된 상점)";
            case USED_PRODUCT -> "(삭제된 중고상품)";
        };
    }
}