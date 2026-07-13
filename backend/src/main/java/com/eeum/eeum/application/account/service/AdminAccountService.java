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
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final StoreRepository storeRepository;
    private final StoreLocationResolver storeLocationResolver;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    private final AccountMapper accountMapper;
    private final OwnerApplicationMapper ownerApplicationMapper;
    private final StoreApprovalMapper storeApprovalMapper;
    private final OwnerStoreWithdrawalService ownerStoreWithdrawalService;
    private final ApplicationEventPublisher eventPublisher;

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

        return accountRepository.searchAccounts(status, role, keyword, pageable)
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
        // Pessimistic Write Lock — suspend와 forceDelete가 동시에 실행될 때 상태 충돌 방지
        Account target = accountRepository.findByIdWithLock(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        target.suspend();

        // DB 커밋 성공 후 Refresh Token 삭제
        eventPublisher.publishEvent(AccountTokenCleanupEvent.refreshOnly(targetAccountId));

        log.info("회원 정지: adminId={}, targetId={}", adminId, targetAccountId);
    }

    @Transactional
    public void activateAccount(Long adminId, Long targetAccountId) {
        Account target = accountRepository.findByIdWithLock(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        target.activate();

        log.info("회원 정지 해제: adminId={}, targetId={}", adminId, targetAccountId);
    }

    @Transactional
    public void cancelWithdrawal(Long adminId, Long targetAccountId) {
        Account target = accountRepository.findByIdWithLock(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        target.cancelWithdrawal();

        log.info("탈퇴 취소: adminId={}, targetId={}", adminId, targetAccountId);
    }

    @Transactional
    public void forceDeleteAccount(Long adminId, Long targetAccountId) {
        // Pessimistic Write Lock — suspend와 forceDelete가 동시에 실행될 때 상태 충돌 방지
        Account target = accountRepository.findByIdWithLock(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        // 사장 계정이면 상점/상품/이벤트 상품을 비활성화 — AccountService.withdraw와 동일하게 처리해야
        // 강제 탈퇴한 사장의 상점이 사용자 화면에 계속 노출되고 주문/예약이 들어오는 것을 막는다.
        if (target.getRole() == AccountRole.ROLE_OWNER) {
            ownerStoreWithdrawalService.deactivateForWithdrawal(targetAccountId);
        }

        target.withdraw();

        // DB 커밋 성공 후 Refresh Token 삭제
        eventPublisher.publishEvent(AccountTokenCleanupEvent.refreshOnly(targetAccountId));

        log.info("회원 강제 탈퇴 처리: adminId={}, targetId={}", adminId, targetAccountId);
    }

    // ===================== 관리자 - 사장 승인/거절 =====================

    @Transactional(readOnly = true)
    public Page<OwnerApplicationListResponseDto> getOwnerRequests(
            OwnerInfoSearchDto condition,
            Pageable pageable
    ) {
        OwnerInfoSearchDto effectiveCondition = condition;
        if (condition.getApprovalStatus() == null) {
            effectiveCondition = new OwnerInfoSearchDto();
            effectiveCondition.setApprovalStatus(ApprovalStatus.PENDING);
            effectiveCondition.setBusinessNumber(condition.getBusinessNumber());
            effectiveCondition.setStoreName(condition.getStoreName());
            effectiveCondition.setRequestedFrom(condition.getRequestedFrom());
            effectiveCondition.setRequestedTo(condition.getRequestedTo());
        }

        return ownerInfoRepository.searchOwnerApplications(effectiveCondition, pageable);
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

        AccountRegion accountRegion = accountRegionRepository
                .findByAccount_AccountIdAndRegion_RegionId(
                        account.getAccountId(),
                        store.getRegion().getRegionId()
                )
                .orElseGet(() -> accountRegionRepository.save(
                        AccountRegion.builder()
                                .account(account)
                                .region(store.getRegion())
                                .verified(true)
                                .verifiedAt(LocalDateTime.now())
                                .build()
                ));

        if (!accountRegion.isVerified()) {
            accountRegion.verify();
        }

        account.setPrimaryRegion(accountRegion.getAccountRegionId());

        ownerInfo.approve();

        createDefaultVisitReservationSettingIfNotExists(store);

        // DB 커밋 성공 후 Refresh Token 삭제 (강제 재로그인으로 승격된 ROLE_OWNER 토큰 발급)
        // 롤백 시 ROLE_OWNER 미승격 상태이므로 토큰이 유지되어야 함
        eventPublisher.publishEvent(AccountTokenCleanupEvent.refreshOnly(account.getAccountId()));

        log.info("사장 승인: adminId={}, ownerInfoId={}, accountId={}, storeId={}",
                adminId, ownerInfoId, account.getAccountId(), store.getStoreId());
    }

    @Transactional
    public void rejectOwner(Long adminId, Long ownerInfoId, RejectRequestDto request) {
        OwnerInfo ownerInfo = ownerInfoRepository.findById(ownerInfoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        if (ownerInfo.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_APPROVED);
        }

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