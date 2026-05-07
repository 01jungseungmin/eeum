package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.ChangePasswordRequestDto;
import com.eeum.eeum.application.account.dto.request.OwnerInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.RejectRequestDto;
import com.eeum.eeum.application.account.dto.request.UpdateInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.WithdrawRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountDetailResponseDto;
import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.MyPageResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerResponseDto;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.util.MaskingUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    // ===================== 내 정보 조회 =====================

    @Transactional(readOnly = true)
    public MyPageResponseDto getMyPage(Long accountId) {
        Account account = getActiveAccount(accountId);
        List<AccountRegion> regions = accountRegionRepository.findByAccount_AccountId(accountId);

        return MyPageResponseDto.builder()
                .accountId(account.getAccountId())
                .email(MaskingUtil.maskEmail(account.getEmail()))
                .nickname(account.getNickname())
                .name(MaskingUtil.maskName(account.getName()))
                .profileImageUrl(account.getProfileImageUrl())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .provider(account.getProvider().name())
                .primaryRegionId(account.getPrimaryRegionId())
                .regions(regions.stream()
                        .map(region -> toRegionDto(region, account))
                        .toList())
                .createdAt(account.getCreatedAt())
                .build();
    }

    // ===================== 내 정보 수정 =====================

    @Transactional
    public AccountResponseDto updateMyInfo(Long accountId, UpdateInfoRequestDto request) {
        Account account = getActiveAccount(accountId);

        if (isEmptyUpdateRequest(request)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        // 닉네임 중복 확인
        // 변경하려는 닉네임이 있고, 현재 닉네임과 다를 때만 중복 검사
        if (request.getNickname() != null
                && !request.getNickname().equals(account.getNickname())
                && accountRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_NICKNAME);
        }

        account.updateInfo(request.getNickname(), request.getProfileImageUrl());

        return toAccountResponseDto(account);
    }

    // ===================== 비밀번호 변경 =====================

    @Transactional
    public void changePassword(Long accountId, ChangePasswordRequestDto request) {
        // 1. ReAuth 토큰 검증
        // TokenService 내부에서 현재 accountId와 토큰 accountId 일치 여부까지 확인
        tokenService.validateReAuthToken(accountId, request.getReAuthToken());

        Account account = getActiveAccount(accountId);

        // 2. OAuth 계정은 로컬 비밀번호 변경 불가
        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 3. 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 4. 새 비밀번호 저장
        account.changePassword(passwordEncoder.encode(request.getNewPassword()));

        // 5. ReAuthToken 삭제
        tokenService.consumeReAuthToken(accountId);

        // 6. 기존 Refresh Token 삭제
        // 비밀번호 변경 후 기존 로그인 유지 차단
        tokenService.deleteRefreshToken(accountId);

        log.info("비밀번호 변경 완료: accountId={}", accountId);
    }

    // ===================== 회원 탈퇴 =====================

    @Transactional
    public void withdraw(Long accountId, WithdrawRequestDto request) {
        // 1. ReAuth 토큰 검증
        tokenService.validateReAuthToken(accountId, request.getReAuthToken());

        // 2. 활성 회원 조회
        Account account = getActiveAccount(accountId);

        // 3. 탈퇴 처리
        account.withdraw();

        // 4. ReAuthToken 삭제
        tokenService.consumeReAuthToken(accountId);

        // 5. Refresh Token 삭제
        tokenService.deleteRefreshToken(accountId);


        log.info("회원 탈퇴 처리 완료: accountId={}", accountId);
    }

    // ===================== FCM 토큰 =====================

    @Transactional
    public void updateFcmToken(Long accountId, String fcmToken) {
        Account account = getActiveAccount(accountId);
        account.updateFcmToken(fcmToken);
    }

    // ===================== 사장 정보 조회 =====================

    @Transactional(readOnly = true)
    public OwnerResponseDto getMyOwnerInfo(Long accountId) {
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        return toOwnerResponseDto(ownerInfo);
    }

    // ===================== 사장 정보 수정 =====================

    @Transactional
    public void updateOwnerInfo(Long accountId, OwnerInfoRequestDto request) {
        Account account = getActiveAccount(accountId);

        if (account.isOAuthAccount()) {
            // 필요 없다면 이 검사는 제거해도 됨
            // 사업자 정보 수정은 OAuth 여부와 직접 관련은 없지만,
            // 현재 정책에 따라 유지/삭제 결정 가능
        }

        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        // 사업자번호 변경 시 중복 확인
        if (request.getBusinessNumber() != null
                && !request.getBusinessNumber().equals(ownerInfo.getBusinessNumber())
                && ownerInfoRepository.existsByBusinessNumber(request.getBusinessNumber())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER);
        }

        ownerInfo.updateInfo(request.getPhone(), request.getBusinessNumber());

        log.info("사장 정보 수정 완료: accountId={}", accountId);
    }

    // ===================== 관리자 - 탈퇴 예정 회원 목록 =====================

    @Transactional(readOnly = true)
    public Page<AccountResponseDto> getWithdrawnAccounts(Pageable pageable) {
        return accountRepository.findByStatus(AccountStatus.WITHDRAWN, pageable)
                .map(this::toAccountResponseDto);
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
        //         .map(this::toAccountResponseDto);

        return accountRepository.findAll(pageable)
                .map(this::toAccountResponseDto);
    }

    @Transactional(readOnly = true)
    public AccountDetailResponseDto getAccountDetail(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        List<AccountRegion> regions = accountRegionRepository.findByAccount_AccountId(accountId);
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId).orElse(null);

        return AccountDetailResponseDto.builder()
                .accountId(account.getAccountId())
                .email(account.getEmail()) // 관리자는 마스킹 없이 전체 조회
                .nickname(account.getNickname())
                .name(account.getName())
                .profileImageUrl(account.getProfileImageUrl())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .provider(account.getProvider().name())
                .emailVerified(account.isEmailVerified())
                .regions(regions.stream()
                        .map(region -> toRegionDto(region, account))
                        .toList())
                .ownerInfo(ownerInfo != null ? toOwnerResponseDto(ownerInfo) : null)
                .createdAt(account.getCreatedAt())
                .deletedAt(account.getDeletedAt())
                .build();
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

        // 정지된 회원은 기존 Refresh Token으로 재발급하지 못하도록 삭제
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

        // 물리 삭제(accountRepository.delete)는 연관 데이터 FK 문제를 만들 수 있으므로
        // 강제 탈퇴도 상태 변경 방식으로 처리
        target.withdraw();

        tokenService.deleteRefreshToken(targetAccountId);

        log.info("회원 강제 탈퇴 처리: adminId={}, targetId={}", adminId, targetAccountId);
    }

    // ===================== 관리자 - 사장 승인/거절 =====================

    @Transactional(readOnly = true)
    public Page<AccountDetailResponseDto> getOwnerRequests(
            Pageable pageable,
            ApprovalStatus approvalStatus
    ) {
        ApprovalStatus status = approvalStatus != null
                ? approvalStatus
                : ApprovalStatus.PENDING;

        return ownerInfoRepository.findByApprovalStatus(status, pageable)
                .map(ownerInfo -> getAccountDetail(ownerInfo.getAccount().getAccountId()));
    }

    public OwnerResponseDto getOwnerApplicationDetail(Long ownerInfoId){
        OwnerInfo ownerInfo = ownerInfoRepository.findById(ownerInfoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        return toOwnerAdminResponseDto(ownerInfo);
    }

    @Transactional
        public void approveOwner(Long adminId, Long ownerInfoId) {
        OwnerInfo ownerInfo = ownerInfoRepository.findById(ownerInfoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));
        Account account = ownerInfo.getAccount();

        // 1. 사장 신청 승인 상태로 변경
        ownerInfo.approve();

        // 2. 계정 권한 변경
        account.approveOwner();

        // 사용자는 다시 로그인하거나 재발급 흐름에서 새 권한을 받아야 함
        tokenService.deleteRefreshToken(account.getAccountId());

        log.info("사장 승인: adminId={}, ownerInfoId={}, accountId={}",
                adminId, ownerInfoId, account.getAccountId());
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

    private Account getActiveAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        return account;
    }

    private boolean isEmptyUpdateRequest(UpdateInfoRequestDto request) {
        return request.getNickname() == null
                && request.getProfileImageUrl() == null;
    }

    private AccountResponseDto toAccountResponseDto(Account account) {
        return AccountResponseDto.builder()
                .accountId(account.getAccountId())
                .email(MaskingUtil.maskEmail(account.getEmail()))
                .nickname(account.getNickname())
                .name(MaskingUtil.maskName(account.getName()))
                .profileImageUrl(account.getProfileImageUrl())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .provider(account.getProvider().name())
                .primaryRegionId(account.getPrimaryRegionId())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private AccountRegionResponseDto toRegionDto(AccountRegion accountRegion, Account account) {
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

    private OwnerResponseDto toOwnerResponseDto(OwnerInfo ownerInfo) {
        return OwnerResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(ownerInfo.getAccount().getAccountId())
                .phone(MaskingUtil.maskPhone(ownerInfo.getPhone()))
                .businessNumber(MaskingUtil.maskBusinessNumber(ownerInfo.getBusinessNumber()))
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .createdAt(ownerInfo.getCreatedAt())
                .build();
    }

    private OwnerResponseDto toOwnerAdminResponseDto(OwnerInfo ownerInfo) {
        return OwnerResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(ownerInfo.getAccount().getAccountId())
                .phone(ownerInfo.getPhone())
                .businessNumber(ownerInfo.getBusinessNumber())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .createdAt(ownerInfo.getCreatedAt())
                .build();
    }
}