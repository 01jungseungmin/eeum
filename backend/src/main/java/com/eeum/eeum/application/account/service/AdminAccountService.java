package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.OwnerInfoSearchDto;
import com.eeum.eeum.application.account.dto.request.RejectRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountDetailResponseDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationDetailResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationListResponseDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.application.store.service.StoreLocationResolver;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final TokenService tokenService;
    private final StoreRepository storeRepository;
    private final StoreLocationResolver storeLocationResolver;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    private final AccountMapper accountMapper;
    private final OwnerApplicationMapper ownerApplicationMapper;
    private final StoreApprovalMapper storeApprovalMapper;

    // ===================== 관리자 - 탈퇴 예정 회원 목록 =====================

    @Transactional(readOnly = true)
    public Page<AccountResponseDto> getWithdrawnAccounts(Pageable pageable) {
        return accountRepository.findByStatus(AccountStatus.WITHDRAWN, pageable)
                .map(accountMapper::toAccountResponseDto);
    }

    // ===================== 관리자 - 회원 목록/상세 =====================

    @Transactional(readOnly = true)
    public Page<AccountResponseDto> getAccounts(
            Pageable pageable,
            AccountStatus status,
            AccountRole role,
            String keyword
    ) {

        return accountRepository.findAll(pageable)
                .map(accountMapper::toAccountResponseDto);
    }

    @Transactional(readOnly = true)
    public AccountDetailResponseDto getAccountDetail(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        List<AccountRegion> regions = accountRegionRepository.findByAccount_AccountId(accountId);
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId).orElse(null);

        return accountMapper.toAccountDetailResponseDto(account, regions, ownerInfo);
    }

    // ===================== 관리자 - 회원 상태 변경 =====================

    @Transactional
    public void suspendAccount(Long adminId, Long targetAccountId) {
        Account target = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        target.suspend();

        tokenService.deleteRefreshToken(targetAccountId);

        log.info("회원 정지: adminId={}, targetId={}", adminId, targetAccountId);
    }

    @Transactional
    public void activateAccount(Long adminId, Long targetAccountId) {
        Account target = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        target.activate();

        log.info("회원 정지 해제: adminId={}, targetId={}", adminId, targetAccountId);
    }

    @Transactional
    public void cancelWithdrawal(Long adminId, Long targetAccountId) {
        Account target = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        target.cancelWithdrawal();

        log.info("탈퇴 취소: adminId={}, targetId={}", adminId, targetAccountId);
    }

    @Transactional
    public void forceDeleteAccount(Long adminId, Long targetAccountId) {
        Account target = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        target.withdraw();

        tokenService.deleteRefreshToken(targetAccountId);

        log.info("회원 강제 탈퇴 처리: adminId={}, targetId={}", adminId, targetAccountId);
    }

    // ===================== 관리자 - 사장 승인/거절 =====================

    @Transactional(readOnly = true)
    public Page<OwnerApplicationListResponseDto> getOwnerRequests(
            OwnerInfoSearchDto condition,
            Pageable pageable
    ) {
        if (condition.getApprovalStatus() == null) {
            condition.setApprovalStatus(ApprovalStatus.PENDING);
        }

        return ownerInfoRepository.searchOwnerApplications(condition, pageable);
    }

    @Transactional(readOnly = true)
    public OwnerApplicationDetailResponseDto getOwnerApplicationDetail(Long ownerInfoId) {
        OwnerInfo ownerInfo = ownerInfoRepository.findById(ownerInfoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        Account account = ownerInfo.getAccount();

        Store store = storeRepository.findByAccount_AccountId(account.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        return toOwnerApplicationDetailDto(ownerInfo, store);
    }

    @Transactional
    public void approveOwner(Long adminId, Long ownerInfoId) {
        OwnerInfo ownerInfo = ownerInfoRepository.findById(ownerInfoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        Account account = ownerInfo.getAccount();

        Store store = storeRepository.findByAccount_AccountId(account.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        if (ownerInfo.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_APPROVED);
        }

        if (store.getRegion() == null || store.getLatitude() == null || store.getLongitude() == null) {
            storeLocationResolver.resolveAndApplyLocation(store);
        }

        account.updatePrimaryRegion(store.getRegion().getRegionId());

        boolean exists = accountRegionRepository
                .existsByAccount_AccountIdAndRegion_RegionId(
                        account.getAccountId(),
                        store.getRegion().getRegionId()
                );

        if (!exists) {
            AccountRegion accountRegion = AccountRegion.builder()
                    .account(account)
                    .region(store.getRegion())
                    .verified(true)
                    .verifiedAt(LocalDateTime.now())
                    .build();

            accountRegionRepository.save(accountRegion);
        }

        ownerInfo.approve();

        createDefaultVisitReservationSettingIfNotExists(store);

        tokenService.deleteRefreshToken(account.getAccountId());

        log.info("사장 승인: adminId={}, ownerInfoId={}, accountId={}, storeId={}",
                adminId, ownerInfoId, account.getAccountId(), store.getStoreId());
    }

    @Transactional
    public void rejectOwner(Long adminId, Long ownerInfoId, RejectRequestDto request) {
        OwnerInfo ownerInfo = ownerInfoRepository.findById(ownerInfoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        Account account = ownerInfo.getAccount();

        ownerInfo.reject(request.getReason());

        log.info("사장 거절: adminId={}, ownerInfoId={}, accountId={}",
                adminId, ownerInfoId, account.getAccountId());
    }

    // ===================== 내부 유틸 =====================

    private OwnerApplicationDetailResponseDto toOwnerApplicationDetailDto(
            OwnerInfo ownerInfo,
            Store store
    ) {
        List<StoreBusinessHourResponseDto> businessHours = getBusinessHours(store.getStoreId());

        return ownerApplicationMapper.toOwnerAdminStoreResponseDto(ownerInfo,store,businessHours);
    }

    private List<StoreBusinessHourResponseDto> getBusinessHours(Long storeId) {
        return storeBusinessHourRepository.findByStore_StoreId(storeId)
                .stream()
                .sorted(Comparator.comparingInt(hour -> hour.getDayOfWeek().getOrder()))
                .map(storeApprovalMapper::toBusinessHourDto)
                .toList();
    }

    private void createDefaultVisitReservationSettingIfNotExists(Store store) {
        boolean exists = storeVisitReservationSettingRepository
                .existsByStore_StoreId(store.getStoreId());

        if (exists) {
            return;
        }

        StoreVisitReservationSetting setting =
                StoreVisitReservationSetting.createDefault(store);

        storeVisitReservationSettingRepository.save(setting);
    }
}