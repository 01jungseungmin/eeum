package com.eeum.eeum.application.store.service;

import com.eeum.eeum.domain.account.repository.WithdrawalObligationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementAccountDeleteServiceTest {
    @Mock private StoreRepository stores;
    @Mock private SettlementAccountRepository settlementAccounts;
    @Mock private WithdrawalObligationRepository obligations;
    @InjectMocks private SettlementAccountDeleteService service;

    @Test
    void 미지급_정산이_있으면_계좌를_보존한다() {
        when(obligations.requiresSettlementAccount(1L)).thenReturn(true);

        assertThat(service.deleteWhenNoPayoutObligation(1L)).isFalse();

        verifyNoInteractions(stores, settlementAccounts);
    }

    @Test
    void 모든_지급이_끝난_후에만_계좌를_파기한다() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(10L);
        when(stores.findByAccountIdWithPessimisticLock(1L)).thenReturn(Optional.of(store));

        assertThat(service.deleteWhenNoPayoutObligation(1L)).isTrue();

        verify(settlementAccounts).deleteByStore_StoreId(10L);
    }
}
