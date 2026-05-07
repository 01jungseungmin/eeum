package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.LocationDto;
import com.eeum.eeum.application.account.dto.request.RegionRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Location;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.LocationRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRegionService {

    private static final int MAX_REGION_COUNT = 2;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final LocationRepository locationRepository;

    // ===================== 활동 지역 목록 조회 =====================

    @Transactional(readOnly = true)
    public List<AccountRegionResponseDto> getRegions(Long accountId) {
        Account account = getAccount(accountId);
        return accountRegionRepository.findByAccount_AccountId(accountId)
                .stream()
                .map(ar -> toDto(account,ar))
                .toList();
    }

    // ===================== 활동 지역 등록 =====================

    @Transactional
    public AccountRegionResponseDto addRegion(Long accountId, RegionRequestDto request) {
        Account account = getAccount(accountId);

        // 1. 최대 2개 제한
        if (accountRegionRepository.countByAccount_AccountId(accountId) >= MAX_REGION_COUNT) {
            throw new BusinessException(ErrorCode.REGION_MAX_LIMIT_EXCEEDED);
        }

        // 2. 지역 존재 확인
        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));

        // 3. 중복 등록 확인
        if (accountRegionRepository.existsByAccount_AccountIdAndRegion_RegionId(accountId, region.getRegionId())) {
            throw new BusinessException(ErrorCode.REGION_ALREADY_REGISTERED);
        }

        // 4. 등록 (verified = false)
        AccountRegion accountRegion = AccountRegion.create(account, region);
        accountRegionRepository.save(accountRegion);

        log.info("활동 지역 등록: accountId={}, regionId={}", accountId, region.getRegionId());
        return toDto(account,accountRegion);
    }

    // ===================== GPS 인증 =====================

    @Transactional
    public AccountRegionResponseDto verifyRegion(Long accountId, Long accountRegionId, LocationDto request) {
        Account account = getAccount(accountId);
        AccountRegion accountRegion = getOwnedAccountRegion(accountRegionId, accountId);
        Region region = accountRegion.getRegion();

        // GPS 거리 계산 (Haversine 공식)
        // Region의 중심 좌표는 Location 엔티티에서 조회해야 하지만
        // Location은 별도 조회가 필요 → 여기서는 Region.radius(미터) 기준으로 검증
        Location location = locationRepository.findByRegion_RegionId(region.getRegionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_LOCATION_NOT_FOUND));
        double distanceKm = calculateDistance(
                request.getLatitude(),
                request.getLongitude(),
                location.getLatitude(),
                location.getLongitude()
        );
        if (distanceKm * 1000 > region.getRadius()) {
            throw new BusinessException(ErrorCode.REGION_GPS_MISMATCH);
        }

        accountRegion.verify();

        //첫 번째 지역이면 자동으로 대표 지역 설정
        if (account.getPrimaryRegionId() == null) {
            account.setPrimaryRegion(accountRegion.getAccountRegionId());
        }

        log.info("활동 지역 GPS 인증 완료: accountId={}, accountRegionId={}", accountId, accountRegionId);
        return toDto(account,accountRegion);
    }

    // ===================== 특정 지역 조회 =====================
    @Transactional(readOnly = true)
    public AccountRegionResponseDto getRegion(Long accountId, Long accountRegionId) {
        Account account = getAccount(accountId);
        AccountRegion accountRegion = getOwnedAccountRegion(accountRegionId, accountId);

        return toDto(account,accountRegion);
    }

    // ===================== 대표 지역 설정 =====================

    @Transactional
    public void setPrimaryRegion(Long accountId, Long accountRegionId) {
        Account account = getAccount(accountId);
        AccountRegion accountRegion = getOwnedAccountRegion(accountRegionId, accountId);

        // 인증된 지역만 대표 지역으로 설정 가능
        if (!accountRegion.isVerified()) {
            throw new BusinessException(ErrorCode.REGION_NOT_VERIFIED);
        }

        account.setPrimaryRegion(accountRegion.getAccountRegionId());
        log.info("대표 지역 설정: accountId={}, regionId={}", accountId, accountRegion.getRegionId());
    }

    // ===================== 활동 지역 삭제 =====================

    @Transactional
    public void deleteRegion(Long accountId, Long accountRegionId) {
        Account account = getAccount(accountId);
        AccountRegion accountRegion = getOwnedAccountRegion(accountRegionId, accountId);

        boolean isPrimaryRegion = accountRegion.getAccountRegionId().equals(account.getPrimaryRegionId());

        // 대표 지역 삭제 시 인증된 지역이 존재 시 변경 없을 시 null
        if (isPrimaryRegion) {
            List<AccountRegion> remainingVerifiedRegions =
                    accountRegionRepository.findByAccount_AccountIdAndVerifiedTrueAndAccountRegionIdNot(
                            accountId,
                            accountRegionId
                    );

            if (remainingVerifiedRegions.isEmpty()) {
                account.clearPrimaryRegion();
            } else {
                AccountRegion nextPrimaryRegion = remainingVerifiedRegions.get(0);
                account.setPrimaryRegion(nextPrimaryRegion.getAccountRegionId());
            }
        }

        accountRegionRepository.delete(accountRegion);
        log.info("활동 지역 삭제: accountId={}, accountRegionId={}", accountId, accountRegionId);
    }

    // ===================== 내부 유틸 =====================

    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private AccountRegion getOwnedAccountRegion(Long accountRegionId, Long accountId) {
        return accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(accountRegionId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
    }

    /**
     * Haversine 공식으로 두 좌표 간 거리(km) 계산
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private AccountRegionResponseDto toDto(Account account,AccountRegion accountRegion) {
        return AccountRegionResponseDto.builder()
                .accountRegionId(accountRegion.getAccountRegionId())
                .regionId(accountRegion.getRegionId())
                .siDo(accountRegion.getRegion().getSiDo())
                .gunGu(accountRegion.getRegion().getGunGu())
                .dong(accountRegion.getRegion().getDong())
                .isPrimary(accountRegion.getAccountRegionId().equals(account.getPrimaryRegionId()))
                .verified(accountRegion.isVerified())
                .verifiedAt(accountRegion.getVerifiedAt())
                .createdAt(accountRegion.getCreatedAt())
                .build();
    }

}