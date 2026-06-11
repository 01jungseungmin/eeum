package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerInfoRepository extends JpaRepository<OwnerInfo, Long>,OwnerInfoRepositoryCustom {

    Optional<OwnerInfo> findByAccount_AccountId(Long accountId);

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