package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Region;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByRegionCode(String regionCode);

    Optional<Region> findByGunGuAndDong(String gunGu, String dong);

    @Query("""
        select r
        from Region r
        where not exists (
            select l
            from Location l
            where l.region = r
        )
    """)
    List<Region> findRegionsWithoutLocation(Pageable pageable);

    boolean existsByRegionCode(String regionCode);
}