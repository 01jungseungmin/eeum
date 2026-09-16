package com.eeum.eeum.application.region.service;

import com.eeum.eeum.application.region.dto.response.RegionSearchResponseDto;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.account.repository.RegionNearbyProjection;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionSearchService {

    private static final int SEARCH_LIMIT = 20;
    private static final double DEFAULT_RADIUS_KM = 5.0;
    private static final int NEARBY_LIMIT = 10;

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public List<RegionSearchResponseDto> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        return regionRepository
                .searchByKeyword(keyword.trim(), PageRequest.of(0, SEARCH_LIMIT))
                .stream()
                .map(this::toKeywordDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegionSearchResponseDto> findNearby(
            double latitude,
            double longitude
    ) {
        validateCoordinate(latitude, longitude);

        return regionRepository
                .findNearbyRegions(latitude, longitude, DEFAULT_RADIUS_KM, NEARBY_LIMIT)
                .stream()
                .map(this::toNearbyDto)
                .toList();
    }

    private RegionSearchResponseDto toKeywordDto(Region region) {
        return RegionSearchResponseDto.builder()
                .regionId(region.getRegionId())
                .regionCode(region.getRegionCode())
                .siDo(region.getSiDo())
                .gunGu(region.getGunGu())
                .dong(region.getDong())
                .fullName(toFullName(
                        region.getSiDo(),
                        region.getGunGu(),
                        region.getDong()
                ))
                .build();
    }

    private RegionSearchResponseDto toNearbyDto(RegionNearbyProjection row) {
        return RegionSearchResponseDto.builder()
                .regionId(row.getRegionId())
                .regionCode(row.getRegionCode())
                .siDo(row.getSiDo())
                .gunGu(row.getGunGu())
                .dong(row.getDong())
                .fullName(toFullName(
                        row.getSiDo(),
                        row.getGunGu(),
                        row.getDong()
                ))
                .latitude(row.getLatitude())
                .longitude(row.getLongitude())
                .distance(row.getDistance())
                .build();
    }

    private String toFullName(String siDo, String gunGu, String dong) {
        StringBuilder sb = new StringBuilder();

        if (siDo != null && !siDo.isBlank()) {
            sb.append(siDo);
        }

        if (gunGu != null && !gunGu.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(gunGu);
        }

        if (dong != null && !dong.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(dong);
        }

        return sb.toString();
    }

    private void validateCoordinate(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }
}