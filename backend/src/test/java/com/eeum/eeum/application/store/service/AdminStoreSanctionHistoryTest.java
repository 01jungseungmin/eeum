package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.application.store.mapper.StoreMapper;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStoreSanctionHistoryTest {

    @InjectMocks private AdminStoreService adminStoreService;
    @Mock private StoreRepository storeRepository;
    @Mock private OwnerInfoRepository ownerInfoRepository;
    @Mock private SettlementAccountRepository settlementAccountRepository;
    @Mock private StoreImageRepository storeImageRepository;
    @Mock private StoreNoticeRepository storeNoticeRepository;
    @Mock private StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock private StoreMapper storeMapper;
    @Mock private SanctionHistoryService sanctionHistoryService;

    @Test
    void 상점_정지는_상점_잠금_후_제재_이력을_기록한다() {
        // Given
        Store store = mock(Store.class);
        when(store.getStatus()).thenReturn(StoreStatus.OPEN);
        when(storeRepository.findByIdWithPessimisticLock(20L)).thenReturn(Optional.of(store));

        // When
        adminStoreService.suspendStore(1L, 20L);

        // Then
        verify(storeRepository).findByIdWithPessimisticLock(20L);
        verify(store).suspend();
        verify(sanctionHistoryService)
                .recordDirectStoreAction(20L, SanctionAction.SUSPEND, 1L);
    }

    @Test
    void 상점_정지_해제는_상점_잠금_후_해제_이력을_기록한다() {
        // Given
        Store store = mock(Store.class);
        when(store.getStatus()).thenReturn(StoreStatus.SUSPENDED);
        when(storeRepository.findByIdWithPessimisticLock(20L)).thenReturn(Optional.of(store));

        // When
        adminStoreService.activateStore(1L, 20L);

        // Then
        verify(storeRepository).findByIdWithPessimisticLock(20L);
        verify(store).activate();
        verify(sanctionHistoryService)
                .recordDirectStoreAction(20L, SanctionAction.ACTIVATE, 1L);
    }
}
