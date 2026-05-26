package com.eeum.eeum.application.store.service;

import com.eeum.eeum.domain.account.entity.Location;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.LocationRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class StoreLocationResolver {

    private final RegionRepository regionRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public void resolveAndApplyLocation(Store store) {
        if (!hasText(store.getAddress())) {
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }

        AddressRegionParts parts = parseAddress(store.getAddress());

        Region region = regionRepository.findByGunGuAndDong(parts.gunGu(), parts.dong())
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));

        Location location = locationRepository.findByRegion_RegionId(region.getRegionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR));

        store.updateLocation(
                region,
                location.getLatitude(),
                location.getLongitude()
        );
    }

    private AddressRegionParts parseAddress(String address) {
        String[] parts = address.trim().split("\\s+");

        if (parts.length < 3) {
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }

        return new AddressRegionParts(parts[1], parts[2]);
    }

    private record AddressRegionParts(String gunGu, String dong) {
    }
}