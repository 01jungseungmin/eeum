package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.application.store.service.StoreLocationResolver;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTokenCleanupTest {

    @InjectMocks AdminAccountService adminAccountService;

    @Mock AccountRepository accountRepository;
    @Mock AccountRegionRepository accountRegionRepository;
    @Mock OwnerInfoRepository ownerInfoRepository;
    @Mock StoreRepository storeRepository;
    @Mock StoreLocationResolver storeLocationResolver;
    @Mock StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    @Mock AccountMapper accountMapper;
    @Mock OwnerApplicationMapper ownerApplicationMapper;
    @Mock StoreApprovalMapper storeApprovalMapper;
    @Mock SanctionHistoryService sanctionHistoryService;
    @Mock AccountWithdrawalProcessor accountWithdrawalProcessor;

    // 제재 자격 판정은 Mock으로 두면 관리자 대상 차단·중복 정지 차단이 무력화된다.
    @Spy AccountSanctionPolicy accountSanctionPolicy = new AccountSanctionPolicy();
    @Mock ApplicationEventPublisher eventPublisher;

    // ─────────────────── suspendAccount ───────────────────

    @Test
    void suspendAccount_성공_시_이벤트로_refresh_토큰_정리() {
        // given
        Long adminId = 0L;
        Long targetId = 1L;

        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.suspendAccount(adminId, targetId);

        // then
        verify(target).suspend();
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.refreshOnly(targetId));
    }

    @Test
    void suspendAccount_이미_탈퇴된_계정_이벤트_미발행() {
        // given
        Long targetId = 1L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(true);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> adminAccountService.suspendAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        // WITHDRAWN 최종 상태 우선 — suspend가 실행되지 않아야 함
        verify(target, never()).suspend();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void suspendAccount_관리자_계정은_정지할_수_없다() {
        // given: 자기 자신도 여기 걸린다 — 이 경로는 ROLE_ADMIN 전용이라 actor == target이면 관리자다.
        Long targetId = 1L;
        Account target = mock(Account.class);
        when(target.isAdmin()).thenReturn(true);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when & then: 신고 처리 경로와 같은 오류로 통일한다.
        assertThatThrownBy(() -> adminAccountService.suspendAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_ADMIN_SANCTION_NOT_ALLOWED);

        verify(target, never()).suspend();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void forceDeleteAccount_관리자_계정은_강제_탈퇴시킬_수_없다() {
        // given
        Long targetId = 1L;
        Account target = mock(Account.class);
        when(target.isAdmin()).thenReturn(true);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> adminAccountService.forceDeleteAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_ADMIN_SANCTION_NOT_ALLOWED);

        verify(accountWithdrawalProcessor, never()).process(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void forceDeleteAccount_정지된_계정은_그대로_강제_탈퇴할_수_있다() {
        // given: 정지 → 탈퇴는 정상 순서다. 제재 자격 판정을 하나로 합치면 이 경로가 막힌다.
        Long targetId = 1L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false);
        when(target.isAdmin()).thenReturn(false);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.forceDeleteAccount(0L, targetId);

        // then
        verify(accountWithdrawalProcessor).process(target);
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.refreshOnly(targetId));
    }

    // ─────────────────── forceDeleteAccount ───────────────────

    @Test
    void forceDeleteAccount_성공_시_이벤트로_refresh_토큰_정리() {
        // given
        Long targetId = 2L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.forceDeleteAccount(0L, targetId);

        // then
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.refreshOnly(targetId));
    }

    @Test
    void forceDeleteAccount는_본인_탈퇴와_같은_뒷정리를_한다() {
        // given — 강제 탈퇴만 찜을 남겨두면 탈퇴자의 찜이 상점·게시글 favoriteCount에 계속 잡힌다
        Long targetId = 2L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.forceDeleteAccount(0L, targetId);

        // then
        verify(accountWithdrawalProcessor).process(target);
    }

    @Test
    void forceDeleteAccount_이미_탈퇴된_계정_이벤트_미발행_deletedAt_리셋_방지() {
        // given
        Long targetId = 2L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(true);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> adminAccountService.forceDeleteAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        // withdraw() 재호출 시 deletedAt이 리셋되어 30일 유예 초기화되는 문제 방지
        verify(accountWithdrawalProcessor, never()).process(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── approveOwner ───────────────────

    @Test
    void approveOwner_성공_시_이벤트로_refresh_토큰_정리() {
        // given
        Long adminId = 0L;
        Long ownerInfoId = 10L;
        Long accountId = 3L;

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(100L);

        Store store = mock(Store.class);
        when(store.getRegion()).thenReturn(region);
        when(store.getLatitude()).thenReturn(37.5);
        when(store.getLongitude()).thenReturn(127.0);

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        // 승인 대상은 "접수 완료된 미승인 신청" + "살아 있는 계정"이다.
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(account.isActive()).thenReturn(true);

        // 잠금 순서 account → owner_info. 선행 조회는 잠글 대상을 정하는 ID projection이다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(storeRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(store));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(accountId, 100L))
                .thenReturn(Optional.empty());
        when(accountRegionRepository.save(any())).thenAnswer(inv -> {
            com.eeum.eeum.domain.account.entity.AccountRegion ar = inv.getArgument(0);
            ReflectionTestUtils.setField(ar, "accountRegionId", 50L);
            return ar;
        });
        when(storeVisitReservationSettingRepository.existsByStore_StoreId(any())).thenReturn(true);

        // when
        adminAccountService.approveOwner(adminId, ownerInfoId);

        // then: 승인 + primaryRegion 설정 + VisitReservationSetting 생성 커밋 후 토큰 삭제
        verify(ownerInfo).approve();
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.refreshOnly(accountId));
    }

    @Test
    void approveOwner_이미_승인된_신청_거절_이벤트_미발행() {
        // given
        Long ownerInfoId = 10L;
        Long accountId = 3L;
        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfo.isApproved()).thenReturn(true);

        Account account = mock(Account.class);
        // 잠금 순서 account → owner_info. 선행 조회는 잠글 대상을 정하는 ID projection이다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, ownerInfoId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_ALREADY_APPROVED);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── rejectOwner (승인 완료 거절 방지) ───────────────────

    @Test
    void rejectOwner_승인된_신청_거절_시도_차단() {
        // given
        Long ownerInfoId = 20L;
        Long accountId = 4L;
        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfo.isApproved()).thenReturn(true);

        // 승인과 같은 잠금 규약 — ID projection 선행 조회 후 account → owner_info 순서로 잠근다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(mock(Account.class)));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));

        // when & then
        assertThatThrownBy(() -> adminAccountService.rejectOwner(
                0L,
                ownerInfoId,
                mock(com.eeum.eeum.application.account.dto.request.RejectRequestDto.class)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_ALREADY_APPROVED);
    }

    @Test
    void rejectOwner_PENDING_신청_거절_시_reject_호출() {
        // given
        Long ownerInfoId = 21L;
        Long accountId = 5L;
        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfo.isReviewRequested()).thenReturn(true);

        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(mock(Account.class)));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));

        com.eeum.eeum.application.account.dto.request.RejectRequestDto rejectRequest =
                mock(com.eeum.eeum.application.account.dto.request.RejectRequestDto.class);
        when(rejectRequest.getReason()).thenReturn("요건 미충족");

        // when
        adminAccountService.rejectOwner(0L, ownerInfoId, rejectRequest);

        // then
        verify(ownerInfo).reject("요건 미충족");
    }

    // ─────────────────── activateAccount ───────────────────

    @Test
    void activateAccount_성공_시_findByIdWithLock_사용_및_activate_호출() {
        // given
        Long targetId = 10L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false);
        // 정지 해제는 SUSPENDED 상태에서만 허용된다
        when(target.isSuspended()).thenReturn(true);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.activateAccount(0L, targetId);

        // then: Pessimistic Lock 사용 확인
        verify(accountRepository).findByIdWithLock(targetId);
        verify(target).activate();
    }

    @Test
    void activateAccount_탈퇴_계정이면_activate_미호출() {
        // given
        Long targetId = 10L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(true);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> adminAccountService.activateAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        verify(target, never()).activate();
    }

    @Test
    void activateAccount_계정_없음_시_예외() {
        // given
        Long targetId = 999L;
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminAccountService.activateAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    // ─────────────────── cancelWithdrawal ───────────────────

    @Test
    void cancelWithdrawal_성공_시_findByIdWithLock_사용_및_cancelWithdrawal_호출() {
        // given
        Long targetId = 20L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(true);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.cancelWithdrawal(0L, targetId);

        // then: Pessimistic Lock 사용 확인
        verify(accountRepository).findByIdWithLock(targetId);
        verify(target).cancelWithdrawal();
    }

    @Test
    void cancelWithdrawal_탈퇴_상태가_아니면_cancelWithdrawal_미호출() {
        // given
        Long targetId = 20L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false); // ACTIVE/SUSPENDED 상태
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> adminAccountService.cancelWithdrawal(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);

        verify(target, never()).cancelWithdrawal();
    }

    // ─────────────────── suspendAccount / forceDeleteAccount - findByIdWithLock 검증 ───────────────────

    @Test
    void suspendAccount_findByIdWithLock_사용_검증() {
        // given
        Long targetId = 30L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.suspendAccount(0L, targetId);

        // then: findById가 아닌 findByIdWithLock 사용 확인
        verify(accountRepository).findByIdWithLock(targetId);
        verify(accountRepository, never()).findById(targetId);
    }

    @Test
    void forceDeleteAccount_findByIdWithLock_사용_검증() {
        // given
        Long targetId = 31L;
        Account target = mock(Account.class);
        when(target.isWithdrawn()).thenReturn(false);
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.of(target));

        // when
        adminAccountService.forceDeleteAccount(0L, targetId);

        // then: findById가 아닌 findByIdWithLock 사용 확인
        verify(accountRepository).findByIdWithLock(targetId);
        verify(accountRepository, never()).findById(targetId);
    }

    @Test
    void suspendAccount_계정_없음_시_이벤트_미발행() {
        // given
        Long targetId = 999L;
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminAccountService.suspendAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void forceDeleteAccount_계정_없음_시_이벤트_미발행() {
        // given
        Long targetId = 999L;
        when(accountRepository.findByIdWithLock(targetId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminAccountService.forceDeleteAccount(0L, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── approveOwner - primaryRegion 기준 검증 ───────────────────

    @Test
    void approveOwner_accountRegion_미인증이면_verify_후_setPrimaryRegion_호출() {
        // given
        Long adminId = 0L;
        Long ownerInfoId = 40L;
        Long accountId = 7L;
        Long regionId = 200L;

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);

        Store store = mock(Store.class);
        when(store.getRegion()).thenReturn(region);
        when(store.getLatitude()).thenReturn(37.5);
        when(store.getLongitude()).thenReturn(127.0);

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        // 승인 대상은 "접수 완료된 미승인 신청" + "살아 있는 계정"이다.
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(account.isActive()).thenReturn(true);

        // 미인증 AccountRegion이 이미 존재하는 경우
        com.eeum.eeum.domain.account.entity.AccountRegion existingRegion =
                mock(com.eeum.eeum.domain.account.entity.AccountRegion.class);
        when(existingRegion.isVerified()).thenReturn(false);
        when(existingRegion.getAccountRegionId()).thenReturn(300L);

        // 잠금 순서 account → owner_info. 선행 조회는 잠글 대상을 정하는 ID projection이다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(storeRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(store));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(accountId, regionId))
                .thenReturn(Optional.of(existingRegion));
        when(storeVisitReservationSettingRepository.existsByStore_StoreId(any())).thenReturn(true);

        // when
        adminAccountService.approveOwner(adminId, ownerInfoId);

        // then: verify() 호출 확인
        verify(existingRegion).verify();
        // setPrimaryRegion은 accountRegion.accountRegionId 기준으로 설정
        verify(account).setPrimaryRegion(300L);
    }

    @Test
    void approveOwner_accountRegion_이미_인증됨_verify_미호출_setPrimaryRegion_호출() {
        // given
        Long adminId = 0L;
        Long ownerInfoId = 41L;
        Long accountId = 8L;
        Long regionId = 201L;

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);

        Store store = mock(Store.class);
        when(store.getRegion()).thenReturn(region);
        when(store.getLatitude()).thenReturn(37.5);
        when(store.getLongitude()).thenReturn(127.0);

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        // 승인 대상은 "접수 완료된 미승인 신청" + "살아 있는 계정"이다.
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(account.isActive()).thenReturn(true);

        // 이미 인증된 AccountRegion
        com.eeum.eeum.domain.account.entity.AccountRegion existingRegion =
                mock(com.eeum.eeum.domain.account.entity.AccountRegion.class);
        when(existingRegion.isVerified()).thenReturn(true);
        when(existingRegion.getAccountRegionId()).thenReturn(400L);

        // 잠금 순서 account → owner_info. 선행 조회는 잠글 대상을 정하는 ID projection이다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(storeRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(store));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(accountId, regionId))
                .thenReturn(Optional.of(existingRegion));
        when(storeVisitReservationSettingRepository.existsByStore_StoreId(any())).thenReturn(true);

        // when
        adminAccountService.approveOwner(adminId, ownerInfoId);

        // then: verify() 미호출, setPrimaryRegion은 accountRegionId 기준
        verify(existingRegion, never()).verify();
        verify(account).setPrimaryRegion(400L);
    }

    @Test
    void approveOwner_store_region_null이면_storeLocationResolver_호출() {
        // given
        Long ownerInfoId = 42L;
        Long accountId = 9L;

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(500L);

        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(100L);
        // resolver 호출 전 null, 호출 후 region 반환 (region == null이면 단락 평가로 latitude/longitude 미호출)
        when(store.getRegion()).thenReturn(null).thenReturn(region);

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        // 승인 대상은 "접수 완료된 미승인 신청" + "살아 있는 계정"이다.
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(account.isActive()).thenReturn(true);

        // 잠금 순서 account → owner_info. 선행 조회는 잠글 대상을 정하는 ID projection이다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(storeRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(store));

        // resolver 호출 후 region이 반환되도록 설정
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(accountId, 500L))
                .thenReturn(Optional.empty());
        when(accountRegionRepository.save(any())).thenAnswer(inv -> {
            com.eeum.eeum.domain.account.entity.AccountRegion ar = inv.getArgument(0);
            ReflectionTestUtils.setField(ar, "accountRegionId", 600L);
            return ar;
        });
        when(storeVisitReservationSettingRepository.existsByStore_StoreId(any())).thenReturn(true);

        // when
        adminAccountService.approveOwner(0L, ownerInfoId);

        // then: storeLocationResolver 호출 확인
        verify(storeLocationResolver).resolveAndApplyLocation(store);
    }

    @Test
    void approveOwner_VisitReservationSetting_없으면_기본_설정_생성() {
        // given
        Long ownerInfoId = 43L;
        Long accountId = 10L;
        Long regionId = 600L;

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);

        Store store = mock(Store.class);
        when(store.getRegion()).thenReturn(region);
        when(store.getLatitude()).thenReturn(37.5);
        when(store.getLongitude()).thenReturn(127.0);
        when(store.getStoreId()).thenReturn(200L);

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        // 승인 대상은 "접수 완료된 미승인 신청" + "살아 있는 계정"이다.
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(account.isActive()).thenReturn(true);

        com.eeum.eeum.domain.account.entity.AccountRegion existingRegion =
                mock(com.eeum.eeum.domain.account.entity.AccountRegion.class);
        when(existingRegion.isVerified()).thenReturn(true);
        when(existingRegion.getAccountRegionId()).thenReturn(700L);

        // 잠금 순서 account → owner_info. 선행 조회는 잠글 대상을 정하는 ID projection이다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(storeRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(store));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(accountId, regionId))
                .thenReturn(Optional.of(existingRegion));

        // VisitReservationSetting 없음
        when(storeVisitReservationSettingRepository.existsByStore_StoreId(200L)).thenReturn(false);

        // when
        adminAccountService.approveOwner(0L, ownerInfoId);

        // then: 기본 설정 저장 호출
        verify(storeVisitReservationSettingRepository).save(any());
    }

    @Test
    void approveOwner_VisitReservationSetting_이미_있으면_저장_미호출() {
        // given
        Long ownerInfoId = 44L;
        Long accountId = 11L;
        Long regionId = 601L;

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);

        Store store = mock(Store.class);
        when(store.getRegion()).thenReturn(region);
        when(store.getLatitude()).thenReturn(37.5);
        when(store.getLongitude()).thenReturn(127.0);
        when(store.getStoreId()).thenReturn(201L);

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        // 승인 대상은 "접수 완료된 미승인 신청" + "살아 있는 계정"이다.
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(account.isActive()).thenReturn(true);

        com.eeum.eeum.domain.account.entity.AccountRegion existingRegion =
                mock(com.eeum.eeum.domain.account.entity.AccountRegion.class);
        when(existingRegion.isVerified()).thenReturn(true);
        when(existingRegion.getAccountRegionId()).thenReturn(800L);

        // 잠금 순서 account → owner_info. 선행 조회는 잠글 대상을 정하는 ID projection이다.
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(ownerInfoId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(storeRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(store));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(accountId, regionId))
                .thenReturn(Optional.of(existingRegion));

        // VisitReservationSetting 이미 있음
        when(storeVisitReservationSettingRepository.existsByStore_StoreId(201L)).thenReturn(true);

        // when
        adminAccountService.approveOwner(0L, ownerInfoId);

        // then: 저장 미호출
        verify(storeVisitReservationSettingRepository, never()).save(any());
    }
}
