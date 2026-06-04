package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteBatchCheckRequestDto;
import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.dto.response.*;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreImage;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final StoreImageRepository storeImageRepository;

    // ===================== 찜 토글 =====================

    /**
     * 찜 토글 — 이미 찜한 상태면 해제, 없으면 등록.
     * refType별로 대상 도메인 존재 여부를 검증한 뒤 처리한다.
     */
    @Transactional
    public FavoriteToggleResponseDto toggleFavorite(
            Long accountId,
            FavoriteToggleRequestDto request
    ) {
        validateRef(request.getRefType(), request.getRefId());

        Optional<Favorite> existing = favoriteRepository
                .findByAccount_AccountIdAndRefTypeAndRefId(
                        accountId, request.getRefType(), request.getRefId());

        if (existing.isPresent()) {
            return removeFavorite(accountId, existing.get(), request.getRefType(), request.getRefId());
        } else {
            return addFavorite(accountId, request.getRefType(), request.getRefId());
        }
    }

    /**
     * 찜 삭제 — favoriteId 기반.
     * 내 찜 목록 화면처럼 favoriteId를 이미 알고 있을 때 사용한다.
     */
    @Transactional
    public void deleteFavorite(Long accountId, Long favoriteId) {
        Favorite favorite = favoriteRepository
                .findByFavoriteIdAndAccount_AccountId(favoriteId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));

        favoriteRepository.delete(favorite);
        favoriteRepository.flush(); // DELETE 즉시 반영 후 카운트 감소
        decrementCount(favorite.getRefType(), favorite.getRefId());

        log.info("찜 삭제(id): favoriteId={}, accountId={}", favoriteId, accountId);
    }

    /**
     * 찜 삭제 — refType + refId 기반.
     * 상점 상세 화면처럼 storeId만 알고 favoriteId를 모를 때 사용한다.
     * 별도 check API 호출 없이 바로 삭제할 수 있어 왕복 횟수를 줄인다.
     */
    @Transactional
    public void deleteFavoriteByRef(Long accountId, FavoriteRefType refType, Long refId) {
        Favorite favorite = favoriteRepository
                .findByAccount_AccountIdAndRefTypeAndRefId(accountId, refType, refId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));

        favoriteRepository.delete(favorite);
        favoriteRepository.flush(); // DELETE 즉시 반영 후 카운트 감소
        decrementCount(refType, refId);

        log.info("찜 삭제(ref): accountId={}, refType={}, refId={}", accountId, refType, refId);
    }

    // ===================== 내 찜 목록 조회 =====================

    /**
     * 내 찜 전체 목록 — refType 무관, 최신순.
     */
    @Transactional(readOnly = true)
    public Page<FavoriteResponseDto> getMyFavorites(Long accountId, Pageable pageable) {
        return favoriteRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(FavoriteResponseDto::from);
    }

    /**
     * 상점 찜 목록 — Store + 썸네일을 IN절 배치 조회로 N+1 방지.
     * Favorite 1번 + Store 1번 + Thumbnail 1번 = 총 3 쿼리.
     */
    @Transactional(readOnly = true)
    public Page<FavoriteStoreResponseDto> getMyFavoriteStores(Long accountId, Pageable pageable) {
        Page<Favorite> favorites = favoriteRepository
                .findByAccount_AccountIdAndRefTypeOrderByCreatedAtDesc(
                        accountId, FavoriteRefType.STORE, pageable);

        if (favorites.isEmpty()) {
            return favorites.map(fav -> null); // 빈 페이지 빠른 반환
        }

        // storeId 목록 추출 → Store, Thumbnail 배치 조회
        List<Long> storeIds = favorites.stream()
                .map(Favorite::getRefId)
                .collect(Collectors.toList());

        Map<Long, Store> storeMap = storeRepository.findAllById(storeIds).stream()
                .collect(Collectors.toMap(Store::getStoreId, Function.identity()));

        Map<Long, String> thumbnailMap = storeImageRepository
                .findByStore_StoreIdInAndIsThumbnailTrue(storeIds).stream()
                .collect(Collectors.toMap(
                        img -> img.getStore().getStoreId(),
                        StoreImage::getImageUrl
                ));

        return favorites.map(fav -> {
            Store store = storeMap.get(fav.getRefId());
            if (store == null) {
                // 찜 등록 후 상점이 삭제된 경우 — 방어적 처리
                log.warn("찜 목록 조회 중 삭제된 상점 발견: refId={}", fav.getRefId());
                throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
            }
            String thumbnail = thumbnailMap.get(store.getStoreId());
            return FavoriteStoreResponseDto.of(fav.getFavoriteId(), store, thumbnail, fav.getCreatedAt());
        });
    }

    // ===================== 찜 여부 확인 =====================

    /**
     * 단건 찜 여부 조회 (상세 화면 진입 시).
     */
    @Transactional(readOnly = true)
    public FavoriteCheckResponseDto checkFavorite(
            Long accountId,
            FavoriteRefType refType,
            Long refId
    ) {
        Optional<Favorite> favorite = favoriteRepository
                .findByAccount_AccountIdAndRefTypeAndRefId(accountId, refType, refId);
        boolean favorited = favorite.isPresent();
        Long favoriteId = favorite.map(Favorite::getFavoriteId).orElse(null);
        return FavoriteCheckResponseDto.of(refType, refId, favorited, favoriteId);
    }

    /**
     * 배치 찜 여부 조회 — 목록 화면 N+1 방지 (QueryDSL IN절 한 번).
     */
    @Transactional(readOnly = true)
    public List<FavoriteCheckResponseDto> checkFavoritesBatch(
            Long accountId,
            FavoriteBatchCheckRequestDto request
    ) {
        Set<Long> favoritedIds = favoriteRepository
                .findFavoriteRefIdsByAccountIdAndType(
                        accountId, request.getRefType(), request.getRefIds());

        return request.getRefIds().stream()
                .map(refId -> FavoriteCheckResponseDto.of(
                        request.getRefType(),
                        refId,
                        favoritedIds.contains(refId),
                        null   // 배치 조회에서는 favoriteId 미제공 (성능 우선)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 대상별 총 찜 수 조회 (상세 화면 표시용).
     */
    @Transactional(readOnly = true)
    public long getFavoriteCount(FavoriteRefType refType, Long refId) {
        return favoriteRepository.countByRefTypeAndRefId(refType, refId);
    }

    // ===================== 내부 CASCADE (다른 서비스 호출) =====================

    /**
     * 대상 도메인 삭제 시 연관 찜 일괄 삭제.
     * (UsedProduct, Store 삭제 시 해당 Service에서 호출)
     */
    @Transactional
    public void deleteAllByRefTypeAndRefId(FavoriteRefType refType, Long refId) {
        long count = favoriteRepository.countByRefTypeAndRefId(refType, refId);
        favoriteRepository.deleteAllByRefTypeAndRefId(refType, refId);
        log.info("찜 CASCADE 삭제: refType={}, refId={}, count={}", refType, refId, count);
    }

    /**
     * 회원 탈퇴 시 해당 회원의 찜 일괄 삭제.
     * 찜한 Store들의 favoriteCount를 먼저 감소한 뒤 레코드를 일괄 삭제한다.
     */
    @Transactional
    public void deleteAllByAccountId(Long accountId) {
        // STORE 찜에 대해 favoriteCount 원자 감소 (USED_PRODUCT 구현 후 분기 추가)
        favoriteRepository.findByAccount_AccountIdAndRefType(accountId, FavoriteRefType.STORE)
                .forEach(f -> storeRepository.decrementFavoriteCount(f.getRefId()));

        favoriteRepository.deleteAllByAccount_AccountId(accountId);
        log.info("회원 탈퇴 찜 CASCADE 삭제: accountId={}", accountId);
    }

    // ===================== 내부 헬퍼 =====================

    private FavoriteToggleResponseDto addFavorite(
            Long accountId, FavoriteRefType refType, Long refId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        Favorite favorite = Favorite.create(account, refType, refId);

        try {
            favoriteRepository.saveAndFlush(favorite);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 요청으로 UNIQUE 제약 위반 → 이미 찜 상태로 응답
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        incrementCount(refType, refId);

        long count = favoriteRepository.countByRefTypeAndRefId(refType, refId);
        log.info("찜 등록: accountId={}, refType={}, refId={}", accountId, refType, refId);
        return FavoriteToggleResponseDto.added(favorite, count);
    }

    private FavoriteToggleResponseDto removeFavorite(
            Long accountId, Favorite favorite, FavoriteRefType refType, Long refId) {

        favoriteRepository.delete(favorite);
        favoriteRepository.flush(); // DELETE 즉시 반영 후 카운트 감소
        decrementCount(refType, refId);

        long count = favoriteRepository.countByRefTypeAndRefId(refType, refId);
        log.info("찜 해제: accountId={}, refType={}, refId={}", accountId, refType, refId);
        return FavoriteToggleResponseDto.removed(refType, refId, count);
    }

    /**
     * refType별 대상 도메인 존재 여부 검증.
     * FK 제약이 없으므로 Service 레이어에서 직접 검증한다. (SDD 명세)
     */
    private void validateRef(FavoriteRefType refType, Long refId) {
        switch (refType) {
            case STORE -> {
                if (!storeRepository.existsById(refId)) {
                    throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
                }
            }
            case USED_PRODUCT -> {
                // UsedProduct 도메인 구현 후 UsedProductRepository.existsById(refId) 검증 추가
                log.debug("USED_PRODUCT 존재 검증 — 도메인 구현 후 활성화: refId={}", refId);
            }
        }
    }

    /**
     * DB 원자 UPDATE로 찜 카운트 +1.
     * Store만 구현 (UsedProduct는 도메인 구현 후 분기 추가).
     */
    private void incrementCount(FavoriteRefType refType, Long refId) {
        if (refType == FavoriteRefType.STORE) {
            storeRepository.incrementFavoriteCount(refId);
        }
        // USED_PRODUCT: usedProductRepository.incrementFavoriteCount(refId);
    }

    /**
     * DB 원자 UPDATE로 찜 카운트 -1 (favoriteCount > 0 가드).
     */
    private void decrementCount(FavoriteRefType refType, Long refId) {
        if (refType == FavoriteRefType.STORE) {
            storeRepository.decrementFavoriteCount(refId);
        }
        // USED_PRODUCT: usedProductRepository.decrementFavoriteCount(refId);
    }
}
