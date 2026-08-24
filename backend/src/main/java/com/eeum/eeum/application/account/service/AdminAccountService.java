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
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
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
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
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
    private final AccountWithdrawalProcessor accountWithdrawalProcessor;
    private final AccountSanctionPolicy accountSanctionPolicy;
    private final SanctionHistoryService sanctionHistoryService;
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

        // 잠금을 얻은 뒤 자격을 확인한다. 이 검사가 없으면 동시 요청이 직렬화된 뒤에도
        // 두 번째 요청이 SUSPEND 제재 이력을 한 건 더 남긴다.
        // 판정은 신고 처리 경로와 같은 정책을 쓴다 — 관리자 대상 차단이 한쪽에만 있으면 구멍이 된다.
        accountSanctionPolicy.validateSuspendable(target);

        target.suspend();
        sanctionHistoryService.recordDirectAccountAction(
                targetAccountId,
                SanctionAction.SUSPEND,
                adminId
        );

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
        // 정지 해제는 정지 상태에서만 의미가 있다. 이 검사가 없으면 PENDING 계정에 해제를 호출했을 때
        // Account.activate()가 status를 ACTIVE로 바꿔 가입 절차를 건너뛴 채 활성 계정이 된다.
        if (!target.isSuspended()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_SUSPENDED);
        }

        target.activate();
        sanctionHistoryService.recordDirectAccountAction(
                targetAccountId,
                SanctionAction.ACTIVATE,
                adminId
        );

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

        // 정지 계정도 강제 탈퇴 대상이다 — validateSuspendable을 쓰면 정지 → 탈퇴 흐름이 막힌다.
        accountSanctionPolicy.validateForceWithdrawable(target);

        // 본인 탈퇴와 같은 뒷정리를 한다 — 상점 비활성화, 탈퇴 처리, 찜 정리.
        // 강제 탈퇴만 찜을 남겨두면 탈퇴자의 찜이 상점·게시글 favoriteCount에 계속 잡힌다.
        accountWithdrawalProcessor.process(target);

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
        // 잠글 계정을 알아내기 위한 선행 조회. 엔티티가 아니라 ID만 읽는다 —
        // 여기서 OwnerInfo를 엔티티로 읽으면 영속성 컨텍스트에 남아 아래 잠금 조회가
        // DB 최신 행 대신 그 인스턴스를 돌려주고, 재조회의 의미가 사라진다.
        Long accountId = ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        // 잠금 순서 account → owner_info. 사업자 정보 수정(AccountService.updateOwnerInfo)과 같은 순서다.
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 잠금을 잡은 뒤 다시 읽는다. 선행 조회 결과를 그대로 쓰면 같은 트랜잭션의 스냅샷·1차 캐시에
        // 묶여 그 사이 커밋된 사업자번호 변경을 보지 못한 채 승인하게 된다.
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccountIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        Store store = storeRepository.findByAccount_AccountId(accountId)
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
