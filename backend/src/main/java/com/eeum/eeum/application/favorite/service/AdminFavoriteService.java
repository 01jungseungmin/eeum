package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.response.FavoriteStatResponseDto;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteStatProjection;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoreRepository storeRepository;

    /**
     * 인기 항목 통계 — 기간 + refType + 상위 N개.
     * refType별로 이름을 조회해 함께 반환한다.
     */
    @Transactional(readOnly = true)
    public List<FavoriteStatResponseDto> getFavoriteStats(
            FavoriteRefType refType,
            LocalDateTime from,
            LocalDateTime to,
            int limit
    ) {
        List<FavoriteStatProjection> projections =
                favoriteRepository.findFavoriteStats(refType, from, to, limit);

        return projections.stream()
                .map(p -> {
                    String refName = resolveRefName(refType, p.getRefId());
                    return FavoriteStatResponseDto.of(refType, p.getRefId(), p.getFavoriteCount(), refName);
                })
                .collect(Collectors.toList());
    }

    /**
     * Store.favoriteCount 정합성 재계산.
     * 장애·대량 삭제·수동 DB 수정 후 favorite 테이블 실제 수와 Store 컬럼이 어긋났을 때 사용한다.
     * 단일 UPDATE ... SELECT로 처리 — N번 쿼리 없이 전체 동기화.
     */
    @Transactional
    public int recalculateFavoriteCounts() {
        int updated = storeRepository.recalculateAllFavoriteCounts();
        log.info("Store.favoriteCount 정합성 재계산 완료: updatedRows={}", updated);
        return updated;
    }

    private String resolveRefName(FavoriteRefType refType, Long refId) {
        return switch (refType) {
            case STORE -> storeRepository.findById(refId)
                    .map(Store::getName)
                    .orElse("(삭제된 상점)");
            case USED_PRODUCT -> "(중고상품 #" + refId + ")";
        };
    }
}