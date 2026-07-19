package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOwnerMetricCommandExecutorTest {

    @InjectMocks
    private AiOwnerMetricCommandExecutor executor;

    @Mock private AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private AccountRepository accountRepository;

    private static final Long STORE_ID = 1L;
    private static final Long OWNER_ID = 100L;
    private static final String YEAR_MONTH = "2026-07";

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        return store;
    }

    private AiOwnerMetricInputRequestDto request(AiMetricType type, String value) {
        return new AiOwnerMetricInputRequestDto(type, new BigDecimal(value), YEAR_MONTH);
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 기존_실측값이_있으면_새로_저장하지_않고_값만_갱신된다() {
        // given
        Store store = stubStore();
        AiOwnerMetricInput existing = AiOwnerMetricInput.create(
                store, AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("500"), YEAR_MONTH);
        when(aiOwnerMetricInputRepository.findByStore_StoreIdAndMetricTypeAndYearMonth(
                eq(STORE_ID), eq(AiMetricType.MONTHLY_POWER_KWH), eq(YEAR_MONTH)))
                .thenReturn(Optional.of(existing));
        when(accountRepository.getReferenceById(OWNER_ID)).thenReturn(mock(Account.class));

        // when
        executor.upsertMetricInTx(store, OWNER_ID, request(AiMetricType.MONTHLY_POWER_KWH, "850.5"));

        // then
        assertThat(existing.getValue()).isEqualByComparingTo("850.5");
        verify(aiOwnerMetricInputRepository, never()).save(any());
        verify(aiActionLogRepository).save(any(AiActionLog.class));
    }

    @Test
    void 기존_실측값이_없으면_새로_저장되고_액션로그가_기록된다() {
        // given
        Store store = stubStore();
        when(aiOwnerMetricInputRepository.findByStore_StoreIdAndMetricTypeAndYearMonth(
                eq(STORE_ID), eq(AiMetricType.MONTHLY_GAS_BILL), eq(YEAR_MONTH)))
                .thenReturn(Optional.empty());
        when(accountRepository.getReferenceById(OWNER_ID)).thenReturn(mock(Account.class));

        // when
        executor.upsertMetricInTx(store, OWNER_ID, request(AiMetricType.MONTHLY_GAS_BILL, "50000"));

        // then
        ArgumentCaptor<AiOwnerMetricInput> metricCaptor = ArgumentCaptor.forClass(AiOwnerMetricInput.class);
        verify(aiOwnerMetricInputRepository).save(metricCaptor.capture());
        AiOwnerMetricInput saved = metricCaptor.getValue();
        assertThat(saved.getMetricType()).isEqualTo(AiMetricType.MONTHLY_GAS_BILL);
        assertThat(saved.getValue()).isEqualByComparingTo("50000");
        assertThat(saved.getYearMonth()).isEqualTo(YEAR_MONTH);

        ArgumentCaptor<AiActionLog> logCaptor = ArgumentCaptor.forClass(AiActionLog.class);
        verify(aiActionLogRepository).save(logCaptor.capture());
        AiActionLog actionLog = logCaptor.getValue();
        assertThat(actionLog.getActionType()).isEqualTo(AiActionType.OWNER_METRIC_INPUT);
        assertThat(actionLog.getTargetType()).isEqualTo(AiMetricType.MONTHLY_GAS_BILL.name());
        assertThat(actionLog.getDescription()).contains(YEAR_MONTH);
        verify(accountRepository).getReferenceById(eq(OWNER_ID));
    }
}
