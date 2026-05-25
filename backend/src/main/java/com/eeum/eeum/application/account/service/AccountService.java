package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.ChangePasswordRequestDto;
import com.eeum.eeum.application.account.dto.request.OwnerInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.UpdateInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.WithdrawRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.MyPageResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerResponseDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.util.MaskingUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AccountMapper accountMapper;

    // ===================== 내 정보 조회 =====================

    @Transactional(readOnly = true)
    public MyPageResponseDto getMyPage(Long accountId) {
        Account account = getActiveAccount(accountId);
        List<AccountRegion> regions = accountRegionRepository.findByAccount_AccountId(accountId);

        return toMyPageResponseDto(account, regions);
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

        return accountMapper.toAccountResponseDto(account);
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

        return accountMapper.toOwnerResponseDto(ownerInfo);
    }

    // ===================== 사장 정보 수정 =====================

    @Transactional
    public void updateOwnerInfo(Long accountId, OwnerInfoRequestDto request) {
        Account account = getActiveAccount(accountId);

        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        // 사업자번호 변경 시 중복 확인
        if (request.getBusinessNumber() != null
                && !request.getBusinessNumber().equals(ownerInfo.getBusinessNumber())
                && ownerInfoRepository.existsByBusinessNumber(request.getBusinessNumber())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER);
        }

        ownerInfo.updateInfo(request.getBusinessNumber());

        log.info("사장 정보 수정 완료: accountId={}", accountId);
    }

    // ===================== 내부 유틸 =====================

    private MyPageResponseDto toMyPageResponseDto(Account account, List<AccountRegion> regions) {
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
                        .map(region -> accountMapper.toRegionDto(region, account))
                        .toList())
                .createdAt(account.getCreatedAt())
                .build();
    }

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

}