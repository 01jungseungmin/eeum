package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long>, AccountRepositoryCustom {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByAccountIdAndDeletedAtIsNull(Long accountId);

    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    Optional<Account> findByProviderAndProviderId(
            OAuthProvider provider,
            String providerId
    );

    // 탈퇴 후 유예가 지난 + 아직 개인정보를 파기하지 않은 계정 ID (스케줄러용).
    // 엔티티가 아니라 ID만, 그것도 한 번에 다 읽지 않고 keyset으로 나눠 읽는다 —
    // backlog가 쌓여 있으면 전체 적재만으로 메모리와 잠금 보유 시간을 밀어낸다.
    // anonymizedAt 조건이 없으면 이미 파기한 계정을 매일 다시 처리한다.
    @Query("""
        SELECT a.accountId FROM Account a
        WHERE a.status = :status
          AND a.deletedAt <= :threshold
          AND a.anonymizedAt IS NULL
          AND a.accountId > :lastAccountId
        ORDER BY a.accountId ASC
        """)
    List<Long> findAnonymizeTargetIdsAfter(
            @Param("status") AccountStatus status,
            @Param("threshold") LocalDateTime threshold,
            @Param("lastAccountId") Long lastAccountId,
            Pageable pageable
    );

    // 무효/만료 FCM 토큰 감지 시 해당 토큰을 null로 초기화
    // Bulk update도 version을 올려 이전에 Account를 읽은 stale 트랜잭션의 덮어쓰기를 차단한다.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Account a
        SET a.fcmToken = null,
            a.version = a.version + 1
        WHERE a.fcmToken = :fcmToken
        """)
    int clearFcmTokenByFcmToken(@Param("fcmToken") String fcmToken);

    // ID만 조회
    @Query("SELECT a.accountId FROM Account a WHERE a.status = 'ACTIVE'")
    List<Long> findAllActiveAccountIds();

    // 관리자 계정 ID 조회 (관리자 알림 수신 대상)
    @Query("SELECT a.accountId FROM Account a WHERE a.role = 'ROLE_ADMIN' AND a.status = 'ACTIVE'")
    List<Long> findAdminAccountIds();

    // 상태 + 토큰 세대를 함께 읽는 projection — WebSocket 인증과 세션 대조에 쓴다.
    @Query("""
        SELECT new com.eeum.eeum.domain.account.repository.AccountAuthState(
                a.accountId, a.status, a.tokenVersion)
        FROM Account a WHERE a.accountId IN :accountIds
        """)
    List<AccountAuthState> findAuthStates(@Param("accountIds") Collection<Long> accountIds);

    // 상태 컬럼만 읽는 projection — 트랜잭션 밖 인가 게이트(WebSocket CONNECT)용.
    // 엔티티를 로딩하면 영속성 컨텍스트도 트랜잭션도 없는 자리에서 불필요한 컬럼까지 끌고 온다.
    @Query("SELECT a.status FROM Account a WHERE a.accountId = :accountId")
    Optional<AccountStatus> findStatusByAccountId(@Param("accountId") Long accountId);

    // 관리자 상태 변경용 비관적 쓰기 잠금 (동시 suspend/forceDelete 경쟁 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdWithLock(@Param("accountId") Long accountId);
}
