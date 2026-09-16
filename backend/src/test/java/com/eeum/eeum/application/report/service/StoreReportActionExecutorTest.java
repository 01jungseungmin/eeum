package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;

@ExtendWith(MockitoExtension.class)
class StoreReportActionExecutorTest {

    @InjectMocks private StoreReportActionExecutor executor;

    @Mock private StoreRepository storeRepository;
    @Mock private ReportedAccountActionService reportedAccountActionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long STORE_ID = 10L;
    private static final Long OWNER_ID = 20L;

    @Test
    void 상점_정지는_상점_행을_잠그고_상태를_SUSPENDED로_변경한다() {
        // Given
        Store store = createStore();
        when(storeRepository.findByIdWithPessimisticLock(STORE_ID)).thenReturn(Optional.of(store));

        // When
        Long result = executor.execute(ReportAction.SUSPEND_STORE, STORE_ID, OWNER_ID, "운영 정책 위반");

        // Then
        assertThat(result).isEqualTo(OWNER_ID);
        assertThat(store.getStatus()).isEqualTo(StoreStatus.SUSPENDED);
        verify(storeRepository).findByIdWithPessimisticLock(STORE_ID);
        verify(eventPublisher).publishEvent(isA(ReportActionNotificationEvent.class));
        verify(reportedAccountActionService, never()).apply(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void 상점_소유자_정지는_신고_접수_시_저장한_소유자_ID로_회원_제재를_위임한다() {
        // Given
        when(reportedAccountActionService.apply(ReportAction.SUSPEND_AUTHOR, OWNER_ID))
                .thenReturn(OWNER_ID);

        // When
        Long result = executor.execute(
                ReportAction.SUSPEND_AUTHOR,
                STORE_ID,
                OWNER_ID,
                "반복 위반"
        );

        // Then
        assertThat(result).isEqualTo(OWNER_ID);
        verify(storeRepository, never()).findByIdWithPessimisticLock(STORE_ID);
        verify(eventPublisher).publishEvent(isA(ReportActionNotificationEvent.class));
    }

    @Test
    void 상점에_게시글_삭제_조치를_적용할_수_없다() {
        assertThatThrownBy(() ->
                executor.execute(ReportAction.DELETE_POST, STORE_ID, OWNER_ID, "잘못된 조치"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
    }

    private Store createStore() {
        Account owner = Account.createOwner(
                "owner@test.com", "encoded", "사장님", "010-1111-2222");
        ReflectionTestUtils.setField(owner, "accountId", OWNER_ID);
        Store store = Store.createForOwnerSignup(owner, "테스트 상점", "서울", "02-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", STORE_ID);
        store.open();
        return store;
    }
}
