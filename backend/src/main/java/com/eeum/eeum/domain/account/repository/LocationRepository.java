package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Location;
import com.eeum.eeum.domain.account.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    boolean existsByRegion(Region region);

    Optional<Location> findByRegion(Region region);

    Optional<Location> findByRegion_RegionId(Long regionId);

    // 활동 지역 목록 응답에 지도 중심 좌표를 실을 때 쓴다.
    // 지역마다 한 건씩 조회하면 목록 크기만큼 쿼리가 늘어난다.
    List<Location> findByRegion_RegionIdIn(Collection<Long> regionIds);
}