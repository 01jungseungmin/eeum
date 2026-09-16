package com.eeum.eeum.application.region.service;

import com.eeum.eeum.application.region.dto.response.Coordinate;
import com.eeum.eeum.domain.account.entity.Location;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.LocationRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LocationSyncService {

    private final RegionRepository regionRepository;
    private final LocationRepository locationRepository;
    private final GeocodingService geocodingService;

    public void syncMissingLocations(int size) {
        List<Region> regions = regionRepository.findRegionsWithoutLocation(
                PageRequest.of(0, size)
        );

        log.info("좌표 저장 대상 Region 개수 = {}", regions.size());

        for (Region region : regions) {
            saveLocation(region);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        log.info("좌표 동기화 1회 실행 완료 - 처리 대상 개수={}", regions.size());
    }

    private void saveLocation(Region region) {
        if (locationRepository.existsByRegion(region)) {
            return;
        }

        String address = region.getFullName();

        Coordinate coordinate = geocodingService.getCoordinate(address);

        if (coordinate == null) {
            log.warn("좌표 조회 실패 - regionId={}, address={}",
                    region.getRegionId(),
                    address);
            return;
        }

        Location location = Location.create(
                region,
                coordinate.getLatitude(),
                coordinate.getLongitude()
        );

        Location savedLocation = locationRepository.save(location);

        log.info("좌표 저장 완료 - locationId={}, regionId={}, address={}, lat={}, lng={}",
                savedLocation.getLocationId(),
                region.getRegionId(),
                address,
                coordinate.getLatitude(),
                coordinate.getLongitude()
        );
    }

}