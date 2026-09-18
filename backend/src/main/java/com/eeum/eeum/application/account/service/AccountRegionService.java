package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.RegionRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.application.account.mapper.AccountRegionMapper;
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

    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final LocationRepository locationRepository;
    private final AccountRegionMapper accountRegionMapper;

    // ===================== 활동 지역 목록 조회 =====================

    @Transactional(readOnly = true)
    public List<AccountRegionResponseDto> getRegions(Long accountId) {
        Account account = getAccount(accountId);
        return accountRegionMapper.toRegionDtos(
                accountRegionRepository.findByAccount_AccountId(accountId), account);
    }

    // 좌표가 등록되지 않은 지역은 null이 그대로 응답에 실린다 — 조회를 실패시키지 않는다.
    private Location findLocation(Long regionId) {
        return locationRepository.findByRegion_RegionId(regionId).orElse(null);
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
        return accountRegionMapper.toRegionDto(accountRegion, account, findLocation(region.getRegionId()));
    }

    // ===================== GPS 인증 =====================
    // 좌표 대조는 외부 API라 AccountRegionVerificationService가 트랜잭션 밖에서 하고,
    // 여기는 그 앞뒤의 조회·저장 구간만 맡는다.

    @Transactional(readOnly = true)
    public String getRegionCode(Long accountId, Long accountRegionId) {
        return getOwnedAccountRegion(accountRegionId, accountId).getRegion().getRegionCode();
    }

    /**
     * 현재 좌표가 이 지역의 법정동임을 확인한 뒤에만 호출한다.
     */
    @Transactional
    public AccountRegionResponseDto completeVerification(Long accountId, Long accountRegionId) {
        Account account = getAccount(accountId);
        AccountRegion accountRegion = getOwnedAccountRegion(accountRegionId, accountId);

        accountRegion.verify();

        //첫 번째 지역이면 자동으로 대표 지역 설정
        if (account.getPrimaryRegionId() == null) {
            account.setPrimaryRegion(accountRegion.getAccountRegionId());
        }

        log.info("활동 지역 GPS 인증 완료: accountId={}, accountRegionId={}", accountId, accountRegionId);
        return accountRegionMapper.toRegionDto(accountRegion, account, findLocation(accountRegion.getRegionId()));
    }

    // ===================== 특정 지역 조회 =====================
    @Transactional(readOnly = true)
    public AccountRegionResponseDto getRegion(Long accountId, Long accountRegionId) {
        Account account = getAccount(accountId);
        AccountRegion accountRegion = getOwnedAccountRegion(accountRegionId, accountId);

        return accountRegionMapper.toRegionDto(
                accountRegion, account, findLocation(accountRegion.getRegionId()));
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

}