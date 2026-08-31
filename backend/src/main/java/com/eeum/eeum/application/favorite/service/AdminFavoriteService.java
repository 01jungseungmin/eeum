package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.response.FavoriteRecalculateResponseDto;
import com.eeum.eeum.application.favorite.dto.response.FavoriteStatResponseDto;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
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
import java.util.EnumMap;
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
        validatePeriod(from, to);

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

    // favoriteCount 정합성 재계산 — 장애·대량 삭제·수동 DB 수정으로 favorite 테이블 실제 수와
    // 대상 컬럼이 어긋났을 때 사용한다. refType을 주면 그 타입만, 생략하면 전체를 맞춘다.
    // 타입별로 단일 UPDATE ... SELECT로 처리 — N번 쿼리 없이 전체 동기화.
    @Transactional
    public FavoriteRecalculateResponseDto recalculateFavoriteCounts(FavoriteRefType refType) {
        Map<FavoriteRefType, Integer> updatedRows = new EnumMap<>(FavoriteRefType.class);

        for (FavoriteRefType type : targetTypes(refType)) {
            updatedRows.put(type, recalculate(type));
        }

        log.info("favoriteCount 정합성 재계산 완료: updatedRows={}", updatedRows);
        return FavoriteRecalculateResponseDto.of(updatedRows);
    }

    private List<FavoriteRefType> targetTypes(FavoriteRefType refType) {
        return refType == null ? List.of(FavoriteRefType.values()) : List.of(refType);
    }

    private int recalculate(FavoriteRefType refType) {
        return switch (refType) {
            case STORE -> storeRepository.recalculateAllFavoriteCounts();
            case USED_PRODUCT -> usedProductRepository.recalculateAllFavoriteCounts();
        };
    }

    // 기간이 뒤집히면 결과가 반드시 빈다. 빈 통계로 응답하면 관리자는 "그 기간에 찜이 없었다"로
    // 읽어 잘못된 조건을 계속 보낸다. 두 값을 함께 봐야 하는 검증이라 애노테이션으로는 표현할 수 없다.
    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException(ErrorCode.FAVORITE_INVALID_PERIOD);
        }
    }

    // 통계 대상 이름 배치 조회 — 대상이 사라진 refId는 map에 담기지 않아 삭제 표기로 넘어간다.
    // 중고 게시글은 Soft Delete라 deletedAt 조건 없이 조회하면 삭제된 글의 제목이 그대로 노출된다.
    private Map<Long, String> resolveRefNames(FavoriteRefType refType, List<Long> refIds) {
        if (refIds.isEmpty()) {
            return Map.of();
        }
        return switch (refType) {
            case STORE -> storeRepository.findAllById(refIds).stream()
                    .collect(Collectors.toMap(Store::getStoreId, Store::getName));
            case USED_PRODUCT -> usedProductRepository.findByUsedProductIdInAndDeletedAtIsNull(refIds).stream()
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