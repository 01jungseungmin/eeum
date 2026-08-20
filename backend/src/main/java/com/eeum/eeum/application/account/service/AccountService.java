package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.ChangePasswordRequestDto;
import com.eeum.eeum.application.account.dto.request.OwnerInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.UpdateInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.WithdrawRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.MyPageResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationDetailResponseDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final AccountWithdrawalProcessor accountWithdrawalProcessor;
    private final AccountMapper accountMapper;
    private final OwnerApplicationMapper ownerApplicationMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ===================== 내 정보 조회 =====================

    @Transactional(readOnly = true)
    public MyPageResponseDto getMyPage(Long accountId) {
        Account account = getActiveAccount(accountId);
        List<AccountRegion> regions = accountRegionRepository.findByAccount_AccountId(accountId);

        return accountMapper.toMyPageResponseDto(account, regions);
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

        // 5. DB 커밋 성공 후 ReAuth Token + Refresh Token 삭제
        // DB 롤백 시 reauth/refresh 토큰이 유지되어 사용자가 재시도 가능
        eventPublisher.publishEvent(AccountTokenCleanupEvent.reAuthAndRefresh(accountId));

        log.info("비밀번호 변경 완료: accountId={}", accountId);
    }

    // ===================== 회원 탈퇴 =====================

    @Transactional
    public void withdraw(Long accountId, WithdrawRequestDto request) {
        // 1. ReAuth 토큰 검증
        tokenService.validateReAuthToken(accountId, request.getReAuthToken());

        // 2. 활성 회원 조회 — 탈퇴는 계정 행을 잠근다.
        // 잠그지 않으면 탈퇴 정리(찜 삭제·카운트 감소)와 같은 사용자의 다른 쓰기 요청이 겹쳐
        // 카운터가 이중 감소하거나, 정리가 끝난 뒤 찜·게시글이 다시 생성될 수 있다.
        Account account = getActiveAccountWithLock(accountId);

        // 3. 상점 잠금 선점 → 사장 상점 비활성화 → 탈퇴 → 찜 정리.
        // 관리자 강제 탈퇴와 같은 절차를 써야 한쪽만 고쳐져 어긋나지 않는다.
        accountWithdrawalProcessor.process(account);

        // 4. DB 커밋 성공 후 ReAuth Token + Refresh Token 삭제
        // DB 롤백 시 계정은 ACTIVE 상태이고 토큰도 유지
        eventPublisher.publishEvent(AccountTokenCleanupEvent.reAuthAndRefresh(accountId));

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
    public OwnerApplicationDetailResponseDto getMyOwnerInfo(Long accountId) {
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        return ownerApplicationMapper.toOwnerApplicationResponseDto(ownerInfo);
    }

    // ===================== 사장 정보 수정 =====================

    @Transactional
    public void updateOwnerInfo(Long accountId, OwnerInfoRequestDto request) {
        getActiveAccount(accountId);

        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        String normalizedBusinessNumber = normalizeBusinessNumber(request.getBusinessNumber());

        boolean businessNumberChanged = normalizedBusinessNumber != null
                && !normalizedBusinessNumber.equals(ownerInfo.getBusinessNumber());

        // 승인 완료(APPROVED) 후 사업자번호 변경 차단 — OwnerApprovalService.validateReviewEditable와 동일 정책.
        // 허용하면 OwnerInfo는 PENDING으로 돌아가지만 Account는 ROLE_OWNER로 남아, 미검증 사업자번호로
        // /owner/** 영업을 지속할 수 있다(재심사 없이 권한 유지).
        if (businessNumberChanged && ownerInfo.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_APPROVED);
        }

        if (businessNumberChanged && ownerInfoRepository.existsByBusinessNumber(normalizedBusinessNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER);
        }

        ownerInfo.updateInfo(normalizedBusinessNumber);

        log.info("사장 정보 수정 완료: accountId={}", accountId);
    }

    // ===================== 내부 유틸 =====================

    private Account getActiveAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        assertActive(account);
        return account;
    }

    // 탈퇴 전용 — 계정 행을 잠근 뒤 같은 조건으로 검증한다.
    // 검증은 getActiveAccount와 반드시 동일해야 한다(정지 계정 차단 포함).
    private Account getActiveAccountWithLock(Long accountId) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        assertActive(account);
        return account;
    }

    private void assertActive(Account account) {
        account.assertWritable();
    }

    private boolean isEmptyUpdateRequest(UpdateInfoRequestDto request) {
        return request.getNickname() == null
                && request.getProfileImageUrl() == null;
    }

    private String normalizeBusinessNumber(String businessNumber) {
        if (businessNumber == null || businessNumber.isBlank()) {
            return null;
        }

        return businessNumber.replaceAll("[^0-9]", "");
    }
}