package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface OwnerInfoRepository extends JpaRepository<OwnerInfo, Long>,OwnerInfoRepositoryCustom {

    Optional<OwnerInfo> findByAccount_AccountId(Long accountId);

    // 사업자 정보 쓰기 잠금 — 사장 승인(AdminAccountService.approveOwner)과
    // 사업자 정보 수정(AccountService.updateOwnerInfo)이 같은 행을 다툰다.
    // 잠그지 않으면 last-writer-wins로 "ROLE_OWNER + PENDING + 미검증 사업자번호" 조합이 남는다.
    // 잠금 순서는 account → owner_info다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OwnerInfo o WHERE o.account.accountId = :accountId")
    Optional<OwnerInfo> findByAccountIdWithLock(@Param("accountId") Long accountId);

    // 잠금 대상을 정하기 위한 선행 조회 — 반드시 projection이어야 한다.
    // 엔티티로 읽으면 그 인스턴스가 영속성 컨텍스트에 남아, 뒤따르는 잠금 조회가
    // DB 최신 행이 아니라 1차 캐시의 옛 인스턴스를 그대로 돌려준다.
    @Query("SELECT o.account.accountId FROM OwnerInfo o WHERE o.ownerInfoId = :ownerInfoId")
    Optional<Long> findAccountIdByOwnerInfoId(@Param("ownerInfoId") Long ownerInfoId);

    boolean existsByBusinessNumber(String businessNumber);

    boolean existsByAccount_AccountIdAndApprovalStatus(
            Long accountId,
            ApprovalStatus approvalStatus
    );

    boolean existsByAccount_AccountId(Long accountId);

    Page<OwnerInfo> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

    Page<OwnerInfo> findByApprovalStatusAndReviewRequestedAtIsNotNull(
            ApprovalStatus approvalStatus,
            Pageable pageable
    );

    Optional<OwnerInfo> findByAccount(Account account);

    void deleteByAccount_AccountId(Long accountId);
}