package com.eeum.eeum.application.account.mapper;

import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Location;
import com.eeum.eeum.domain.account.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AccountRegionMapper {

    // 지역 중심 좌표는 별도 테이블에 있다. 지역마다 한 건씩 조회하면 목록 크기만큼
    // 쿼리가 붙으므로, 목록 변환은 아래 toRegionDtos가 한 번에 읽어 채운다.
    private final LocationRepository locationRepository;

    // ===================== 지역 응답 변환 =====================

    /**
     * 활동 지역 목록을 좌표까지 채워 변환한다. 좌표는 한 번의 조회로 모두 읽는다.
     */
    public List<AccountRegionResponseDto> toRegionDtos(List<AccountRegion> regions, Account account) {
        if (regions.isEmpty()) {
            return List.of();
        }
        Map<Long, Location> locations = locationRepository
                .findByRegion_RegionIdIn(regions.stream().map(AccountRegion::getRegionId).toList())
                .stream()
                .collect(Collectors.toMap(l -> l.getRegion().getRegionId(), Function.identity()));

        return regions.stream()
                .map(region -> toRegionDto(region, account, locations.get(region.getRegionId())))
                .toList();
    }


    /**
     * @param location 지역 중심 좌표. 아직 등록되지 않은 지역이 있어 null을 허용한다 —
     *                 좌표가 없다고 지역 조회 자체를 실패시키면 안 된다.
     */
    public AccountRegionResponseDto toRegionDto(
            AccountRegion accountRegion,
            Account account,
            Location location
    ) {
        return AccountRegionResponseDto.builder()
                .latitude(location == null ? null : location.getLatitude())
                .longitude(location == null ? null : location.getLongitude())
                .radius(accountRegion.getRegion().getRadius())
                .accountRegionId(accountRegion.getAccountRegionId())
                .regionId(accountRegion.getRegionId())
                .siDo(accountRegion.getRegion().getSiDo())
                .gunGu(accountRegion.getRegion().getGunGu())
                .dong(accountRegion.getRegion().getDong())
                .isPrimary(Objects.equals(accountRegion.getAccountRegionId(), account.getPrimaryRegionId()))
                .verified(accountRegion.isVerified())
                .verifiedAt(accountRegion.getVerifiedAt())
                .createdAt(accountRegion.getCreatedAt())
                .build();
    }
}
