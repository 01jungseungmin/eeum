package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository
        extends JpaRepository<Favorite, Long>, FavoriteRepositoryCustom {

    // 본인 찜 단건 조회 (소유권 검증용)
    Optional<Favorite> findByFavoriteIdAndAccount_AccountId(Long favoriteId, Long accountId);

    // 잠금 대상을 정하기 위한 선행 조회 — 반드시 projection이어야 한다.
    // 엔티티로 읽으면 그 인스턴스가 영속성 컨텍스트에 남아, 잠금을 잡은 뒤의 재조회가
    // DB 최신 행이 아니라 1차 캐시의 옛 인스턴스를 그대로 돌려준다.
    @Query("""
        SELECT f.refType AS refType, f.refId AS refId
        FROM Favorite f
        WHERE f.favoriteId = :favoriteId AND f.account.accountId = :accountId
        """)
    Optional<FavoriteRefProjection> findRefByFavoriteIdAndAccountId(
            @Param("favoriteId") Long favoriteId, @Param("accountId") Long accountId);

    // 토글 시 기존 찜 조회
    Optional<Favorite> findByAccount_AccountIdAndRefTypeAndRefId(
            Long accountId, FavoriteRefType refType, Long refId);

    // 삭제 직전 재조회 — 일반 SELECT는 REPEATABLE READ 스냅샷을 읽으므로 current read가 아니다.
    // 잠금 조회여야 그 사이 커밋된 삭제를 보고, 이미 사라진 행을 지우려다
    // flush 시점 StaleStateException으로 끝나는 경로를 없앨 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT f FROM Favorite f
        WHERE f.account.accountId = :accountId
          AND f.refType = :refType
          AND f.refId = :refId
        """)
    Optional<Favorite> findForUpdate(
            @Param("accountId") Long accountId,
            @Param("refType") FavoriteRefType refType,
            @Param("refId") Long refId);

    // 찜 여부 확인
    boolean existsByAccount_AccountIdAndRefTypeAndRefId(
            Long accountId, FavoriteRefType refType, Long refId);

    // 내 찜 전체 목록 (refType 무관, 최신순) — 무한 스크롤용.
    // count 쿼리 없이 size+1을 읽어 다음 페이지 여부만 판정한다.
    Slice<Favorite> findSliceByAccount_AccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    // 내 찜 전체 목록 (번호 페이징) — 레거시 경로 GET /favorites/me 전용.
    @Deprecated(forRemoval = true)
    Page<Favorite> findByAccount_AccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    // 타입별 내 찜 목록 (최신순)
    Page<Favorite> findByAccount_AccountIdAndRefTypeOrderByCreatedAtDesc(
            Long accountId, FavoriteRefType refType, Pageable pageable);

    // 대상별 찜 수 (상세 화면용)
    long countByRefTypeAndRefId(FavoriteRefType refType, Long refId);

    // 토글 해제 — 단건 삭제
    void deleteByAccount_AccountIdAndRefTypeAndRefId(
            Long accountId, FavoriteRefType refType, Long refId);

    // 대상 도메인 삭제 시 CASCADE 찜 일괄 삭제 단일 DELETE 쿼리 실행 (파생 쿼리의 N+1 DELETE 방지)
    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.refType = :refType AND f.refId = :refId")
    void deleteAllByRefTypeAndRefId(
            @Param("refType") FavoriteRefType refType, @Param("refId") Long refId);

    // 회원 탈퇴 시 CASCADE 찜 일괄 삭제 단일 DELETE 쿼리 실행 (파생 쿼리의 N+1 DELETE 방지)
    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.account.accountId = :accountId")
    void deleteAllByAccount_AccountId(@Param("accountId") Long accountId);

    // 회원 탈퇴 시 favoriteCount 동기화를 위한 타입별 찜 목록 조회
    List<Favorite> findByAccount_AccountIdAndRefType(Long accountId, FavoriteRefType refType);

    // 사장 통합 고객 목록 — 상점을 찜한 고객 accountId 목록 (합집합 구성 및 찜 여부 판정용)
    @Query("SELECT f.account.accountId FROM Favorite f WHERE f.refType = :refType AND f.refId = :refId")
    List<Long> findAccountIdsByRefTypeAndRefId(
            @Param("refType") FavoriteRefType refType,
            @Param("refId") Long refId
    );
}