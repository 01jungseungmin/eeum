package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteBatchCheckRequestDto;
import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.dto.response.*;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRefProjection;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteStoreRow;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
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
    private final AccountWriteGuard accountWriteGuard;
    private final StoreRepository storeRepository;
    private final StoreImageRepository storeImageRepository;
    private final UsedProductRepository usedProductRepository;
    private final UsedProductImageRepository usedProductImageRepository;

    // ===================== 찜 토글 =====================

    // 찜 토글 — 이미 찜한 상태면 해제, 없으면 등록.
    // 등록만 노출 조건을 검증한다. 해제는 숨김·삭제된 대상이어도 막지 않는다 —
    // 내가 남긴 찜을 거두는 일까지 막으면 카운트가 부풀린 채로 영영 남는다.
    @Transactional
    public FavoriteToggleResponseDto toggleFavorite(
            Long accountId,
            FavoriteToggleRequestDto request
    ) {
        // 잠금 순서는 항상 account → 대상(store/used_product) → favorite다.
        // 한 경로라도 순서를 뒤집으면 탈퇴·삭제 트랜잭션과 교착이 난다.
        Account account = lockActiveAccount(accountId);
        LockedRef target = lockRef(request.getRefType(), request.getRefId());

        Optional<Favorite> existing = favoriteRepository
                .findByAccount_AccountIdAndRefTypeAndRefId(
                        accountId, request.getRefType(), request.getRefId());

        if (existing.isPresent()) {
            return removeFavorite(accountId, existing.get(),
                    request.getRefType(), request.getRefId(), target.publiclyVisible());
        }

        target.assertRegisterable(request.getRefType());
        return addFavorite(account, request.getRefType(), request.getRefId());
    }

    // 찜 삭제 — favoriteId 기반 내 찜 목록 화면처럼 favoriteId를 이미 알고 있을 때 사용
    //
    // 계약: favoriteId는 "지울 대상(refType + refId)을 알아내는 식별자"이고,
    // 실제 삭제 단위는 (accountId, refType, refId)다. 행 하나를 지목해 지우는 API가 아니다.
    //
    // 그래서 조회와 삭제 사이에 같은 사용자가 다른 기기에서 해제 후 재등록하면(ABA),
    // 처음 지목한 A가 아니라 새로 생긴 B가 지워진다. 의도된 동작이다 —
    //   * 쿼리가 accountId로 묶여 있어 남의 찜은 지울 수 없고,
    //   * 잠금 후 current read + 단건 삭제라 카운터는 정확히 한 번만 감소하며,
    //   * 최종 상태가 "그 대상이 찜 해제됨"으로, 삭제 버튼을 누른 사용자의 의도와 같다.
    // 반대로 ID 불일치를 404로 돌려주면 클라이언트에는 실패라고 답하면서 찜은 남아 있는
    // 상태가 되어 더 나쁘다. 특정 행만 지워야 하는 요구가 생기면 그때 별도 경로를 만든다.
    @Transactional
    public void deleteFavorite(Long accountId, Long favoriteId) {
        // 대상을 알아내기 위한 선행 조회(잠금 없음). 엔티티가 아니라 refType·refId만 읽는다 —
        // 여기서 Favorite을 엔티티로 읽으면 영속성 컨텍스트에 남아, 아래 잠금 재조회가
        // DB 최신 행 대신 그 인스턴스를 돌려주고 재조회의 의미가 사라진다.
        FavoriteRefProjection peeked = favoriteRepository
                .findRefByFavoriteIdAndAccountId(favoriteId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));

        deleteFavoriteByRef(accountId, peeked.getRefType(), peeked.getRefId());
        log.info("찜 삭제(id): favoriteId={}, accountId={}", favoriteId, accountId);
    }

    // 찜 삭제 — refType + refId 기반 상점 상세 화면처럼 storeId만 알고 favoriteId를 모를 때 사용

    @Transactional
    public void deleteFavoriteByRef(Long accountId, FavoriteRefType refType, Long refId) {
        lockActiveAccount(accountId);
        lockRef(refType, refId);

        // 잠금을 잡은 뒤 다시 읽는다. 먼저 읽어둔 행으로 지우면 그 사이 대상 삭제 트랜잭션이
        // 같은 찜을 정리한 경우 없는 행을 지우고 카운터만 한 번 더 깎는다.
        // 일반 SELECT는 REPEATABLE READ 스냅샷을 읽어 이미 커밋된 삭제를 보지 못하므로
        // 잠금 조회(current read)여야 한다. 아니면 없는 행을 지우려다 flush에서 409로 끝난다.
        Favorite favorite = favoriteRepository
                .findForUpdate(accountId, refType, refId)
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
        // 정렬은 찜 등록 최신순으로 고정한다. 요청 sort를 그대로 두면 실제 순서와
        // 응답 Page 메타데이터가 달라 클라이언트가 잘못된 순서를 전제하게 된다.
        return favoriteRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountId, latestFirst(pageable))
                .map(FavoriteResponseDto::from);
    }

    // 찜 등록 최신순 + PK tie-break. createdAt 동률 시 페이지 경계에서 항목이 중복·유실된다.
    private Pageable latestFirst(Pageable pageable) {
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("favoriteId")));
    }

    /**
     * 상점 찜 목록.
     * 공개 조건(계정 활성·미정지·사장 승인)은 QueryDSL 조인에서 페이징·count 전에 적용한다 —
     * 조회 후 메모리에서 거르면 페이지 크기·전체 건수·페이지 경계가 모두 어긋난다.
     * 조회 1번 + count 1번 + 대표 사진 1번 = 총 3 쿼리.
     */
    @Transactional(readOnly = true)
    public Slice<FavoriteStoreResponseDto> getMyFavoriteStores(Long accountId, Pageable pageable) {
        Slice<FavoriteStoreRow> rows = favoriteRepository.findFavoriteStoresSlice(accountId, pageable);

        if (rows.isEmpty()) {
            // 썸네일 배치 조회만 건너뛴다. 정렬·다음 페이지 여부는 리포지토리 판정을 그대로 쓴다.
            return new SliceImpl<>(List.of(), rows.getPageable(), rows.hasNext());
        }
        return rows.map(toStoreDto(resolveStoreThumbnails(rows.getContent())));
    }

    /**
     * 상점 찜 목록(번호 페이징) — 레거시 경로 {@code GET /favorites/me/STORE} 전용.
     *
     * <p>이미 배포된 계약이라 응답 형태를 바꿀 수 없어 남겨둔다. 신규 경로
     * {@code GET /favorites/me/store}는 프로젝트 기준대로 Slice를 쓴다.
     * 프론트가 신규 경로로 옮기면 이 메서드와 findFavoriteStores를 함께 지운다.
     */
    @Deprecated(forRemoval = true)
    @Transactional(readOnly = true)
    public Page<FavoriteStoreResponseDto> getMyFavoriteStoresPaged(Long accountId, Pageable pageable) {
        Page<FavoriteStoreRow> rows = favoriteRepository.findFavoriteStores(accountId, pageable);

        if (rows.isEmpty()) {
            // 썸네일 배치 조회만 건너뛴다. 정렬·전체 건수는 리포지토리가 판정한 값을 그대로 쓴다.
            return new PageImpl<>(List.of(), rows.getPageable(), rows.getTotalElements());
        }
        return rows.map(toStoreDto(resolveStoreThumbnails(rows.getContent())));
    }

    // 대표 사진 배치 조회 — 항목마다 조회하면 페이지 크기만큼 쿼리가 나간다(N+1).
    private Map<Long, String> resolveStoreThumbnails(List<FavoriteStoreRow> rows) {
        List<Long> storeIds = rows.stream()
                .map(FavoriteStoreRow::store)
                .map(Store::getStoreId)
                .toList();

        return storeImageRepository
                .findByStore_StoreIdInAndIsThumbnailTrue(storeIds).stream()
                .collect(Collectors.toMap(
                        img -> img.getStore().getStoreId(),
                        StoreImage::getImageUrl,
                        (first, second) -> first));
    }

    private Function<FavoriteStoreRow, FavoriteStoreResponseDto> toStoreDto(Map<Long, String> thumbnailMap) {
        return row -> FavoriteStoreResponseDto.of(
                row.favoriteId(), row.store(),
                thumbnailMap.get(row.store().getStoreId()), row.favoritedAt());
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
            // 요청 pageable을 그대로 돌려주면 실제 적용된 정렬(최신순 고정)과 메타데이터가 어긋난다.
            return new SliceImpl<>(List.of(), rows.getPageable(), rows.hasNext());
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
    // 비회원도 호출할 수 있는 공개 API다. 검증 없이 세면 ID를 훑는 것만으로
    // 숨김 게시글의 존재와 찜 수가 드러나므로, 볼 수 있는 대상인지 먼저 확인한다.
    @Transactional(readOnly = true)
    public long getFavoriteCount(FavoriteRefType refType, Long refId) {
        validateRef(refType, refId);
        return favoriteRepository.countByRefTypeAndRefId(refType, refId);
    }

    // ===================== 내부 CASCADE (다른 서비스 호출) =====================

    // 대상 도메인 삭제 시 연관 찜 일괄 삭제 + 카운트 0 전이.
    // 찜 행만 지우고 favoriteCount를 그대로 두면 삭제된 대상이 예전 수를 계속 들고 있어,
    // 관리자 통계·정합성 재계산 결과와 어긋난다.
    @Transactional
    public void deleteAllByRefTypeAndRefId(FavoriteRefType refType, Long refId) {
        long count = favoriteRepository.countByRefTypeAndRefId(refType, refId);
        favoriteRepository.deleteAllByRefTypeAndRefId(refType, refId);
        resetCount(refType, refId);
        log.info("찜 CASCADE 삭제: refType={}, refId={}, count={}", refType, refId, count);
    }

    // 회원 탈퇴 시 해당 회원의 찜 일괄 삭제.
    @Transactional
    public void deleteAllByAccountId(Long accountId) {
        // 대상 도메인의 favoriteCount 원자 감소 — 탈퇴자가 남긴 찜이 카운트에 계속 잡히면 안 된다.
        // 찜 1건마다 UPDATE를 날리면 찜이 많은 회원의 탈퇴가 그만큼의 쿼리를 유발하므로 IN 절로 한 번에 처리한다.
        List<Long> storeIds = findFavoriteRefIds(accountId, FavoriteRefType.STORE);
        if (!storeIds.isEmpty()) {
            storeRepository.decrementFavoriteCounts(storeIds);
        }

        List<Long> productIds = findFavoriteRefIds(accountId, FavoriteRefType.USED_PRODUCT);
        if (!productIds.isEmpty()) {
            usedProductRepository.decrementFavoriteCounts(productIds);
        }

        favoriteRepository.deleteAllByAccount_AccountId(accountId);
        log.info("회원 탈퇴 찜 CASCADE 삭제: accountId={}, storeCount={}, usedProductCount={}",
                accountId, storeIds.size(), productIds.size());
    }

    // 탈퇴 회원이 찜한 대상 ID 목록 — 빈 목록으로 IN 절을 만들면 DB에 따라 문법 오류가 나므로 호출부에서 거른다.
    // 오름차순으로 정렬한다 — 여러 행을 함께 잠그는 경로끼리 순서가 다르면 교착이 난다.
    public List<Long> findFavoriteRefIds(Long accountId, FavoriteRefType refType) {
        return favoriteRepository.findByAccount_AccountIdAndRefType(accountId, refType).stream()
                .map(Favorite::getRefId)
                .distinct()
                .sorted()
                .toList();
    }

    // ===================== 내부 헬퍼 =====================

    private FavoriteToggleResponseDto addFavorite(
            Account account, FavoriteRefType refType, Long refId) {

        Favorite favorite = Favorite.create(account, refType, refId);

        try {
            favoriteRepository.saveAndFlush(favorite);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 요청으로 UNIQUE 제약 위반 → 이미 찜 상태로 응답
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        incrementCount(refType, refId);

        long count = favoriteRepository.countByRefTypeAndRefId(refType, refId);
        log.info("찜 등록: accountId={}, refType={}, refId={}",
                account.getAccountId(), refType, refId);
        return FavoriteToggleResponseDto.added(favorite, count);
    }

    private FavoriteToggleResponseDto removeFavorite(
            Long accountId, Favorite favorite,
            FavoriteRefType refType, Long refId, boolean publiclyVisible) {

        favoriteRepository.delete(favorite);
        favoriteRepository.flush(); // DELETE 즉시 반영 후 카운트 감소
        decrementCount(refType, refId);

        // 비공개 대상(숨김 글·미승인 상점)의 찜 수는 응답에서 뺀다.
        // 해제는 허용해야 하지만, 정확한 카운트를 돌려주면 공개 카운트 API를 막아둔 의미가 없어진다.
        Long count = publiclyVisible
                ? favoriteRepository.countByRefTypeAndRefId(refType, refId)
                : null;

        log.info("찜 해제: accountId={}, refType={}, refId={}", accountId, refType, refId);
        return FavoriteToggleResponseDto.removed(refType, refId, count);
    }

    // 잠금 순서의 첫 단계 — 규약은 AccountWriteGuard에 있다.
    private Account lockActiveAccount(Long accountId) {
        return accountWriteGuard.lockActive(accountId);
    }

    // 찜 대상 행을 잠그고, 그 시점의 공개 여부를 함께 판정한다.
    // 잠그지 않으면 "공개 상태 확인 → 찜 저장" 사이에 삭제·숨김·사장 탈퇴가 끼어든다.
    // 대상이 아예 없으면(하드 삭제·잘못된 ID) 잠글 것도 없다 — 등록 검증에서 걸러진다.
    private LockedRef lockRef(FavoriteRefType refType, Long refId) {
        return switch (refType) {
            case USED_PRODUCT -> new LockedRef(
                    usedProductRepository.findByUsedProductIdForUpdate(refId)
                            .filter(UsedProduct::isPubliclyVisible)
                            .isPresent());
            // 상점 행을 잠근 뒤 공개 조건을 다시 확인한다.
            case STORE -> new LockedRef(
                    storeRepository.findByIdWithPessimisticLock(refId)
                            .map(store -> storeRepository.isPubliclyVisible(refId))
                            .orElse(false));
        };
    }

    // 잠근 시점의 대상 공개 여부. 등록 가능 여부와 카운트 노출 여부를 함께 결정한다.
    private record LockedRef(boolean publiclyVisible) {

        // 등록은 공개 대상만 허용한다. 존재 사실을 흘리지 않도록 NOT_FOUND로 통일한다.
        // 판매완료(SOLD)는 막지 않는다: 거래가 끝난 뒤에도 기록으로 남길 수 있어야 한다.
        void assertRegisterable(FavoriteRefType refType) {
            if (publiclyVisible) {
                return;
            }
            throw switch (refType) {
                case STORE -> new BusinessException(ErrorCode.STORE_NOT_FOUND);
                case USED_PRODUCT -> new BusinessException(ErrorCode.USED_PRODUCT_NOT_FOUND);
            };
        }
    }

    // 조회 전용 검증 — 잠금 없이 노출 대상인지만 확인한다.
    // readOnly 트랜잭션에서 쓰이므로 쓰기 잠금을 잡으면 안 된다.
    private void validateRef(FavoriteRefType refType, Long refId) {
        switch (refType) {
            case STORE -> {
                if (!storeRepository.isPubliclyVisible(refId)) {
                    throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
                }
            }
            case USED_PRODUCT -> usedProductRepository
                    .findByUsedProductIdAndDeletedAtIsNull(refId)
                    .filter(UsedProduct::isPubliclyVisible)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USED_PRODUCT_NOT_FOUND));
        }
    }

    // 대상 삭제 시 찜 카운트 0 전이.
    private void resetCount(FavoriteRefType refType, Long refId) {
        switch (refType) {
            case STORE -> storeRepository.resetFavoriteCount(refId);
            case USED_PRODUCT -> usedProductRepository.resetFavoriteCount(refId);
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
