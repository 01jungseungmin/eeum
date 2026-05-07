package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    Optional<Account> findByProviderAndProviderId(
            OAuthProvider provider,
            String providerId
    );

    // 탈퇴 후 30일 경과 계정 조회 (스케줄러용)
    @Query("SELECT a FROM Account a WHERE a.status = :status AND a.deletedAt <= :threshold")
    List<Account> findWithdrawnAccountsBefore(
            @Param("status") AccountStatus status,
            @Param("threshold") LocalDateTime threshold
    );
    // 관리자 회원 목록 조회 (동적 필터링은 QueryDSL로 구현 예정)
    Page<Account> findAll(Pageable pageable);
}