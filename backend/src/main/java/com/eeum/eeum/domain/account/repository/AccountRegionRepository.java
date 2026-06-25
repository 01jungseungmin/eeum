package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.AccountRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRegionRepository extends JpaRepository<AccountRegion, Long> {

    List<AccountRegion> findByAccount_AccountId(Long accountId);

    Optional<AccountRegion> findByAccountRegionIdAndAccount_AccountId(
            Long accountRegionId, Long accountId
    );

    boolean existsByAccount_AccountIdAndRegion_RegionId(Long accountId, Long regionId);

    int countByAccount_AccountId(Long accountId);

    List<AccountRegion> findByAccount_AccountIdAndVerifiedTrueAndAccountRegionIdNot(
            Long accountId,
            Long accountRegionId
    );

    void deleteByAccount_AccountId(Long accountId);

    Optional<AccountRegion> findByAccount_AccountIdAndRegion_RegionId(Long accountId, Long regionId);
}