package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long>,StoreRepositoryCustom {

    boolean existsByAccount_AccountId(Long accountId);

    // 가게 단톡방 생성/종료/입장/초대의 DB 공통 mutex.
    // ACTIVE 조건 인덱스를 직접 잠그면 종료 시 생성 컬럼 갱신과 next-key lock 교착이 날 수 있어
    // 식별자가 변하지 않는 Store 행을 먼저 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Store s WHERE s.storeId = :storeId")
    Optional<Store> findByIdWithPessimisticLock(@Param("storeId") Long storeId);

    // 신고 상세의 대상 스냅샷 — 소유자를 함께 조회해 N+1 방지
    @EntityGraph(attributePaths = "account")
    Optional<Store> findWithAccountByStoreId(Long storeId);

    Optional<Store> findByAccount_AccountId(Long accountId);

    void deleteByAccount_AccountId(Long accountId);


    // ===================== 찜 카운트 원자 UPDATE (SDD 명세) =====================

    //찜 카운트 +1 — DB 원자 UPDATE, 영향받은 행 수 반환
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Store s SET s.favoriteCount = s.favoriteCount + 1 WHERE s.storeId = :storeId")
    int incrementFavoriteCount(@Param("storeId") Long storeId);

    // 찜 카운트 -1 — favoriteCount > 0 가드, 영향받은 행 수 반환
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Store s
        SET s.favoriteCount = s.favoriteCount - 1
        WHERE s.storeId = :storeId
          AND s.favoriteCount > 0
        """)
    int decrementFavoriteCount(@Param("storeId") Long storeId);

    // 정합성 재계산 — favorite 테이블 실제 row 수로 모든 상점의 favoriteCount 일괄 갱신
    // 단일 UPDATE ... SELECT로 처리해 N번 쿼리 없이 처리
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE store s
        SET s.favorite_count = (
            SELECT COUNT(*) FROM favorite f
            WHERE f.ref_type = 'STORE' AND f.ref_id = s.store_id
        )
        """, nativeQuery = true)
    int recalculateAllFavoriteCounts();
}
