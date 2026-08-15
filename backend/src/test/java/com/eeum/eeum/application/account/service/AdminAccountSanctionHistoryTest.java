package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.application.store.service.StoreLocationResolver;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountSanctionHistoryTest {

    @InjectMocks private AdminAccountService adminAccountService;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountRegionRepository accountRegionRepository;
    @Mock private OwnerInfoRepository ownerInfoRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreLocationResolver storeLocationResolver;
    @Mock private StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock private StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    @Mock private AccountMapper accountMapper;
    @Mock private OwnerApplicationMapper ownerApplicationMapper;
    @Mock private StoreApprovalMapper storeApprovalMapper;
    @Mock private OwnerStoreWithdrawalService ownerStoreWithdrawalService;
    @Mock private SanctionHistoryService sanctionHistoryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void 회원_정지_성공_시_같은_흐름에서_제재_이력을_기록한다() {
        // Given
        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));

        // When
        adminAccountService.suspendAccount(1L, 10L);

        // Then
        verify(account).suspend();
        verify(sanctionHistoryService)
                .recordDirectAccountAction(10L, SanctionAction.SUSPEND, 1L);
    }

    @Test
    void 회원_정지_해제_성공_시_같은_흐름에서_해제_이력을_기록한다() {
        // Given
        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        // 정지 해제는 SUSPENDED 상태에서만 허용된다
        when(account.isSuspended()).thenReturn(true);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));

        // When
        adminAccountService.activateAccount(1L, 10L);

        // Then
        verify(account).activate();
        verify(sanctionHistoryService)
                .recordDirectAccountAction(10L, SanctionAction.ACTIVATE, 1L);
    }

    @Test
    void 이미_정지된_회원을_다시_정지하면_거부되고_제재_이력이_중복_기록되지_않는다() {
        // Given
        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isSuspended()).thenReturn(true);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));

        // When & Then
        assertThatThrownBy(() -> adminAccountService.suspendAccount(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_ALREADY_SUSPENDED);

        verify(account, never()).suspend();
        verify(sanctionHistoryService, never())
                .recordDirectAccountAction(anyLong(), any(SanctionAction.class), anyLong());
    }

    @Test
    void 정지_상태가_아닌_회원의_해제는_거부되고_해제_이력이_기록되지_않는다() {
        // Given: PENDING/ACTIVE 회원이 해제 대상으로 들어온 경우
        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isSuspended()).thenReturn(false);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));

        // When & Then
        assertThatThrownBy(() -> adminAccountService.activateAccount(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_SUSPENDED);

        // Account.activate()는 status를 ACTIVE로 바꾸고 deletedAt까지 지우므로
        // 호출되면 PENDING 회원이 가입 절차를 건너뛰고 활성화된다.
        verify(account, never()).activate();
        verify(sanctionHistoryService, never())
                .recordDirectAccountAction(anyLong(), any(SanctionAction.class), anyLong());
    }

    @Test
    void 탈퇴_회원은_상태를_바꾸지_않고_제재_이력도_기록하지_않는다() {
        // Given
        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(true);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));

        // When
        assertThatThrownBy(() -> adminAccountService.suspendAccount(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        // Then
        verify(account, never()).suspend();
        verify(sanctionHistoryService, never())
                .recordDirectAccountAction(10L, SanctionAction.SUSPEND, 1L);
    }
}
