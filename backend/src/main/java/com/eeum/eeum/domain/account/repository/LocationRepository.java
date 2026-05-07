package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location,Long> {
    Optional<Location> findByRegion_RegionId(Long regionId);
}
