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
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));

        // When
        adminAccountService.activateAccount(1L, 10L);

        // Then
        verify(account).activate();
        verify(sanctionHistoryService)
                .recordDirectAccountAction(10L, SanctionAction.ACTIVATE, 1L);
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
