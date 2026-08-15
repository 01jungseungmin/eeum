package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerStoreWithdrawalServiceTest {

    @InjectMocks private OwnerStoreWithdrawalService service;
    @Mock private StoreRepository storeRepository;
    @Mock private ProductRepository productRepository;
    @Mock private EventProductRepository eventProductRepository;

    @Test
    void 탈퇴_비활성화는_상점_잠금을_잡고_관리자_정지상태를_보존한다() {
        Long accountId = 1L;
        Store store = Store.createForOwnerSignup(
                mock(Account.class), "정지 상점", "서울", "02-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", 10L);
        store.suspend();
        when(storeRepository.findByAccountIdWithPessimisticLock(accountId))
                .thenReturn(Optional.of(store));
        when(productRepository.findByStore_StoreId(10L)).thenReturn(List.of());
        when(eventProductRepository.findByProduct_Store_StoreIdAndStatusOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        service.deactivateForWithdrawal(accountId);

        assertThat(store.getStatus()).isEqualTo(StoreStatus.SUSPENDED);
        verify(storeRepository).findByAccountIdWithPessimisticLock(accountId);
    }
}
