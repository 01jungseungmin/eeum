package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteBatchCheckRequestDto;
import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.dto.response.*;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteUsedProductRow;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreImage;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
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
    private final UsedProductRepository usedProductRepository;
    private final UsedProductImageRepository usedProductImageRepository;

    // ===================== 찜 토글 =====================

    // 찜 토글 — 이미 찜한 상태면 해제, 없으면 등록 refType별로 대상 도메인 존재 여부를 검증한 뒤 처리
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

    // 찜 삭제 — favoriteId 기반 내 찜 목록 화면처럼 favoriteId를 이미 알고 있을 때 사용

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

    // 찜 삭제 — refType + refId 기반 상점 상세 화면처럼 storeId만 알고 favoriteId를 모를 때 사용

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

    // 내 찜 전체 목록 — refType 무관, 최신순

    @Transactional(readOnly = true)
    public Page<FavoriteResponseDto> getMyFavorites(Long accountId, Pageable pageable) {
        return favoriteRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(FavoriteResponseDto::from);
    }

    // 상점 찜 목록 — Store + 썸네일을 IN절 배치 조회로 N+1 방지
    // Favorite 1번 + Store 1번 + Thumbnail 1번 = 총 3 쿼리.

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

        // 찜 등록 후 상점이 삭제된 dangling 참조는 해당 항목만 건너뛴다 — 예외를 던지면 그 1건 때문에
        // 찜 목록 페이지 전체가 실패해 사용자가 다른 찜을 삭제할 화면조차 열 수 없게 된다.
        List<FavoriteStoreResponseDto> content = favorites.getContent().stream()
                .filter(fav -> {
                    boolean exists = storeMap.containsKey(fav.getRefId());
                    if (!exists) {
                        log.warn("찜 목록 조회 중 삭제된 상점 발견 — 항목 스킵: refId={}", fav.getRefId());
                    }
                    return exists;
                })
                .map(fav -> {
                    Store store = storeMap.get(fav.getRefId());
                    String thumbnail = thumbnailMap.get(store.getStoreId());
                    return FavoriteStoreResponseDto.of(fav.getFavoriteId(), store, thumbnail, fav.getCreatedAt());
                })
                .toList();

        return new PageImpl<>(content, pageable, favorites.getTotalElements());
    }

    /**
     * 중고 게시글 찜 목록.
     * 숨김·삭제 필터는 QueryDSL 조인 조건으로 DB에서 적용한다 — 조회 후 메모리에서 거르면
     * 요청한 size보다 적은 항목이 내려가고 hasNext 판정도 어긋난다.
     * 조회 1번(게시글·지역 조인 포함) + 대표 사진 1번 = 총 2 쿼리.
     */
    @Transactional(readOnly = true)
    public Slice<FavoriteUsedProductResponseDto> getMyFavoriteUsedProducts(
            Long accountId, Pageable pageable) {

        Slice<FavoriteUsedProductRow> rows =
                favoriteRepository.findFavoriteUsedProducts(accountId, pageable);

        if (rows.isEmpty()) {
            return new SliceImpl<>(List.of(), pageable, false);
        }

        List<Long> productIds = rows.getContent().stream()
                .map(FavoriteUsedProductRow::usedProductId)
                .toList();

        Map<Long, String> thumbnailMap = usedProductImageRepository
                .findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(productIds).stream()
                .collect(Collectors.toMap(
                        image -> image.getUsedProduct().getUsedProductId(),
                        UsedProductImage::getImageUrl,
                        (first, second) -> first));

        return rows.map(row -> FavoriteUsedProductResponseDto.of(
                row, thumbnailMap.get(row.usedProductId())));
    }

    // ===================== 찜 여부 확인 =====================

    // 단건 찜 여부 조회 (상세 화면 진입 시)
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

    // 배치 찜 여부 조회 — 목록 화면 N+1 방지 (QueryDSL IN절 한 번)
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

    // 대상별 총 찜 수 조회 (상세 화면 표시용)
    @Transactional(readOnly = true)
    public long getFavoriteCount(FavoriteRefType refType, Long refId) {
        return favoriteRepository.countByRefTypeAndRefId(refType, refId);
    }

    // ===================== 내부 CASCADE (다른 서비스 호출) =====================

    // 대상 도메인 삭제 시 연관 찜 일괄 삭제
    @Transactional
    public void deleteAllByRefTypeAndRefId(FavoriteRefType refType, Long refId) {
        long count = favoriteRepository.countByRefTypeAndRefId(refType, refId);
        favoriteRepository.deleteAllByRefTypeAndRefId(refType, refId);
        log.info("찜 CASCADE 삭제: refType={}, refId={}, count={}", refType, refId, count);
    }

    // 회원 탈퇴 시 해당 회원의 찜 일괄 삭제.
    @Transactional
    public void deleteAllByAccountId(Long accountId) {
        // 대상 도메인의 favoriteCount 원자 감소 — 탈퇴자가 남긴 찜이 카운트에 계속 잡히면 안 된다.
        // 찜 1건마다 UPDATE를 날리면 찜이 많은 회원의 탈퇴가 그만큼의 쿼리를 유발하므로 IN 절로 한 번에 처리한다.
        List<Long> storeIds = refIds(accountId, FavoriteRefType.STORE);
        if (!storeIds.isEmpty()) {
            storeRepository.decrementFavoriteCounts(storeIds);
        }

        List<Long> productIds = refIds(accountId, FavoriteRefType.USED_PRODUCT);
        if (!productIds.isEmpty()) {
            usedProductRepository.decrementFavoriteCounts(productIds);
        }

        favoriteRepository.deleteAllByAccount_AccountId(accountId);
        log.info("회원 탈퇴 찜 CASCADE 삭제: accountId={}, storeCount={}, usedProductCount={}",
                accountId, storeIds.size(), productIds.size());
    }

    // 탈퇴 회원이 찜한 대상 ID 목록 — 빈 목록으로 IN 절을 만들면 DB에 따라 문법 오류가 나므로 호출부에서 거른다.
    private List<Long> refIds(Long accountId, FavoriteRefType refType) {
        return favoriteRepository.findByAccount_AccountIdAndRefType(accountId, refType).stream()
                .map(Favorite::getRefId)
                .toList();
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

    // refType별 대상 도메인 존재 여부 검증
    private void validateRef(FavoriteRefType refType, Long refId) {
        switch (refType) {
            case STORE -> {
                if (!storeRepository.existsById(refId)) {
                    throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
                }
            }
            case USED_PRODUCT -> {
                // existsById가 아니라 삭제 필터가 걸린 조회를 쓴다 — 삭제된 글은 찜할 수 없어야 한다.
                // 숨김 글도 막는다 — ID만 알면 목록을 거치지 않고 찜해 favoriteCount를 올릴 수 있고,
                // 숨김 해제 후 그 카운트가 그대로 노출된다. 존재 사실을 흘리지 않도록 NOT_FOUND로 통일한다.
                // 판매완료(SOLD)는 막지 않는다: 거래가 끝난 뒤에도 기록으로 남길 수 있어야 한다.
                UsedProduct product = usedProductRepository
                        .findByUsedProductIdAndDeletedAtIsNull(refId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USED_PRODUCT_NOT_FOUND));
                if (product.isHidden()) {
                    throw new BusinessException(ErrorCode.USED_PRODUCT_NOT_FOUND);
                }
            }
        }
    }

    // DB 원자 UPDATE로 찜 카운트 +1.
    private void incrementCount(FavoriteRefType refType, Long refId) {
        switch (refType) {
            case STORE -> storeRepository.incrementFavoriteCount(refId);
            case USED_PRODUCT -> usedProductRepository.incrementFavoriteCount(refId);
        }
    }

    // DB 원자 UPDATE로 찜 카운트 -1 (favoriteCount > 0 가드).
    private void decrementCount(FavoriteRefType refType, Long refId) {
        switch (refType) {
            case STORE -> storeRepository.decrementFavoriteCount(refId);
            case USED_PRODUCT -> usedProductRepository.decrementFavoriteCount(refId);
        }
    }
}
