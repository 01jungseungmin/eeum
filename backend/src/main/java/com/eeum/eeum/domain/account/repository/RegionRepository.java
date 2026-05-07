package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByRegionCode(String regionCode);

    boolean existsByRegionCode(String regionCode);
}