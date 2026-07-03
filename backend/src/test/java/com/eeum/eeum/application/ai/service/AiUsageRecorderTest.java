package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.policy.AiPlanPolicy;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiUsageLogRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiUsageRecorderTest {

    @InjectMocks
    private AiUsageRecorder aiUsageRecorder;

    @Mock private AiUsageLogRepository aiUsageLogRepository;
    @Mock private AccountRepository accountRepository;
    @Spy private AiPlanPolicy aiPlanPolicy = new AiPlanPolicy();

    private static final String YEAR_MONTH = "2026-07";
    private static final Long OWNER_ID = 100L;

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(1L);
        lenient().when(accountRepository.getReferenceById(OWNER_ID)).thenReturn(mock(Account.class));
        return store;
    }

    @Test
    void Basic_플랜_월_30회_초과_시_AI_USAGE_LIMIT_EXCEEDED_예외가_발생한다() {
        // given
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(30L);

        // when & then
        assertThatThrownBy(() -> aiUsageRecorder.checkAndRecord(
                store, OWNER_ID, AiPlanType.BASIC, AiUsageType.MARKETING_DRAFT, YEAR_MONTH))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);
        verify(aiUsageLogRepository, never()).save(any());
    }

    @Test
    void Basic_플랜_제한_이내면_사용량_로그가_저장된다() {
        // given
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(5L);

        // when
        aiUsageRecorder.checkAndRecord(store, OWNER_ID, AiPlanType.BASIC, AiUsageType.MARKETING_DRAFT, YEAR_MONTH);

        // then
        verify(aiUsageLogRepository).save(any());
    }

    @Test
    void Pro_플랜은_카운트_조회_없이_사용량_로그만_저장된다() {
        // given
        Store store = stubStore();

        // when
        aiUsageRecorder.checkAndRecord(store, OWNER_ID, AiPlanType.PRO, AiUsageType.MARKETING_DRAFT, YEAR_MONTH);

        // then
        verify(aiUsageLogRepository, never()).countByStore_StoreIdAndYearMonth(anyLong(), anyString());
        verify(aiUsageLogRepository).save(any());
    }

    @Test
    void Free_플랜은_AI_PLAN_REQUIRED_예외가_발생한다() {
        // given — FREE 플랜 limit=0이므로 카운트 조회 없이 즉시 거부되어야 한다
        Store store = stubStore();

        // when & then
        assertThatThrownBy(() -> aiUsageRecorder.checkAndRecord(
                store, OWNER_ID, AiPlanType.FREE, AiUsageType.MARKETING_DRAFT, YEAR_MONTH))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_REQUIRED);
        verify(aiUsageLogRepository, never()).countByStore_StoreIdAndYearMonth(anyLong(), anyString());
        verify(aiUsageLogRepository, never()).save(any());
    }

    @Test
    void Basic_플랜_29회_사용_시_30번째_요청은_성공한다() {
        // given
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(29L);

        // when
        aiUsageRecorder.checkAndRecord(store, OWNER_ID, AiPlanType.BASIC, AiUsageType.MARKETING_DRAFT, YEAR_MONTH);

        // then
        verify(aiUsageLogRepository).save(any());
    }
}
