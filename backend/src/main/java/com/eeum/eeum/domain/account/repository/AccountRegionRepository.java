package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.AccountRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    // 여러 회원의 대표 지역을 한 번에 해석할 때 region까지 함께 읽는다 (N+1 방지)
    @Query("SELECT ar FROM AccountRegion ar JOIN FETCH ar.region WHERE ar.accountRegionId IN :accountRegionIds")
    List<AccountRegion> findAllWithRegionByIdIn(@Param("accountRegionIds") Collection<Long> accountRegionIds);
}
