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

import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class StoreLocationResolver {

    private final RegionRepository regionRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public void resolveAndApplyLocation(Store store) {
        if (!hasText(store.getAddress())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        Region region = resolveRegion(store.getAddress());

        Location location = locationRepository.findByRegion_RegionId(region.getRegionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_LOCATION_NOT_FOUND));

        store.updateLocation(
                region,
                location.getLatitude(),
                location.getLongitude()
        );
    }

    private Region resolveRegion(String address) {
        String normalizedAddress = normalizeAddress(address);
        String[] tokens = normalizedAddress.split("\\s+");

        // 1차: 주소 토큰에서 구/군 + 동 조합을 직접 찾아본다.
        for (int i = 0; i < tokens.length - 1; i++) {
            String gunGu = tokens[i];
            String dong = tokens[i + 1];

            if (isGunGuToken(gunGu) && isDongToken(dong)) {
                return regionRepository.findByGunGuAndDong(gunGu, dong)
                        .orElseGet(() -> findByDongContainingAddress(normalizedAddress, dong));
            }
        }

        // 2차: 행정동 후보를 주소에 포함된 가장 긴 동 이름 기준으로 찾는다.
        return regionRepository.findAll().stream()
                .filter(region -> normalizedAddress.contains(region.getGunGu())
                        && normalizedAddress.contains(region.getDong()))
                .sorted((a, b) -> Integer.compare(b.getDong().length(), a.getDong().length()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
    }

    private Region findByDongContainingAddress(String normalizedAddress, String dong) {
        List<Region> candidates = regionRepository.findAllByDongContaining(dong);

        return candidates.stream()
                .filter(region -> normalizedAddress.contains(region.getGunGu()))
                .sorted((a, b) -> Integer.compare(b.getDong().length(), a.getDong().length()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
    }

    private String normalizeAddress(String address) {
        return address.trim()
                .replace("서울특별시", "서울")
                .replace("부산광역시", "부산")
                .replace("대구광역시", "대구")
                .replace("인천광역시", "인천")
                .replace("광주광역시", "광주")
                .replace("대전광역시", "대전")
                .replace("울산광역시", "울산")
                .replace("세종특별자치시", "세종")
                .replace("경기도", "경기")
                .replace("강원특별자치도", "강원")
                .replace("강원도", "강원")
                .replace("충청북도", "충북")
                .replace("충청남도", "충남")
                .replace("전북특별자치도", "전북")
                .replace("전라북도", "전북")
                .replace("전라남도", "전남")
                .replace("경상북도", "경북")
                .replace("경상남도", "경남")
                .replace("제주특별자치도", "제주")
                .replaceAll("\\s+", " ");
    }

    private boolean isGunGuToken(String token) {
        return token.endsWith("구") || token.endsWith("군") || token.endsWith("시");
    }

    private boolean isDongToken(String token) {
        return token.endsWith("동") || token.endsWith("읍") || token.endsWith("면") || token.matches(".*동[0-9]*가");
    }
}
