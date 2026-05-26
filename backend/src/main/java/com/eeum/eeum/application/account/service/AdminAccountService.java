package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.OwnerInfoSearchDto;
import com.eeum.eeum.application.account.dto.request.RejectRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountDetailResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationListResponseDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationDetailResponseDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.application.region.service.RegionService;
import com.eeum.eeum.application.store.service.StoreLocationResolver;
import com.eeum.eeum.domain.account.entity.*;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.store.entity.Store;
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
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AccountMapper accountMapper;
    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final TokenService tokenService;
    private final StoreRepository storeRepository;
    private final StoreLocationResolver storeLocationResolver;

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
        // TODO:
        // 현재는 필터 파라미터만 받을 수 있게 시그니처를 맞춘 상태.
        // 실제 status / role / keyword 검색은 QueryDSL 또는 Repository 커스텀 쿼리로 구현 필요.
        //
        // 예:
        // return accountQueryRepository.searchAccounts(pageable, status, role, keyword)
        //         .map(accountMapper::toAccountResponseDto);

        return accountRepository.findAll(pageable)
                .map(accountMapper::toAccountResponseDto);
    }

    @Transactional(readOnly = true)
    public AccountDetailResponseDto getAccountDetail(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        List<AccountRegion> regions = accountRegionRepository.findByAccount_AccountId(accountId);
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId).orElse(null);

        return toAccountDetailResponseDto(account, regions, ownerInfo);
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

    private AccountDetailResponseDto toAccountDetailResponseDto(
            Account account,
            List<AccountRegion> regions,
            OwnerInfo ownerInfo
    ) {
        return AccountDetailResponseDto.builder()
                .accountId(account.getAccountId())
                .email(account.getEmail())
                .nickname(account.getNickname())
                .name(account.getName())
                .profileImageUrl(account.getProfileImageUrl())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .provider(account.getProvider().name())
                .emailVerified(account.isEmailVerified())
                .regions(regions.stream()
                        .map(region -> accountMapper.toRegionDto(region, account))
                        .toList())
                .ownerInfo(ownerInfo != null ? accountMapper.toOwnerAdminResponseDto(ownerInfo) : null)
                .createdAt(account.getCreatedAt())
                .deletedAt(account.getDeletedAt())
                .build();
    }

    public OwnerApplicationListResponseDto toOwnerApplicationListDto(
            OwnerInfo ownerInfo,
            Store store
    ) {
        Account account = ownerInfo.getAccount();

        return OwnerApplicationListResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(account.getAccountId())
                .email(account.getEmail())
                .ownerName(account.getName())
                .phone(account.getPhone())
                .businessNumber(ownerInfo.getBusinessNumber())
                .openingDate(ownerInfo.getOpeningDate())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .reviewRequestedAt(ownerInfo.getReviewRequestedAt())
                .storeId(store != null ? store.getStoreId() : null)
                .storeName(store != null ? store.getName() : null)
                .storeAddress(store != null ? store.getAddress() : null)
                .storeStatus(store != null ? store.getStatus().name() : null)
                .createdAt(ownerInfo.getCreatedAt())
                .build();
    }

    private OwnerApplicationDetailResponseDto toOwnerApplicationDetailDto(
            OwnerInfo ownerInfo,
            Store store
    ) {
        Account account = ownerInfo.getAccount();

        return OwnerApplicationDetailResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(account.getAccountId())
                .ownerName(account.getName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .businessNumber(ownerInfo.getBusinessNumber())
                .openingDate(ownerInfo.getOpeningDate())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .reviewRequestedAt(ownerInfo.getReviewRequestedAt())
                .createdAt(ownerInfo.getCreatedAt())
                .storeId(store.getStoreId())
                .storeName(store.getName())
                .storeAddress(store.getAddress())
                .storePhone(store.getPhone())
                .storeCategoryId(
                        store.getCategory() != null
                                ? store.getCategory().getCategoryId()
                                : null
                )
                .storeCategoryName(
                        store.getCategory() != null
                                ? store.getCategory().getName()
                                : null
                )
                .storeDescription(store.getDescription())
                .businessHours(store.getBusinessHours())
                .storeStatus(store.getStatus().name())
                .build();
    }
}