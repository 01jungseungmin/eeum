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
    void Basic_플랜_월_50회_초과_시_AI_USAGE_LIMIT_EXCEEDED_예외가_발생한다() {
        // given
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(50L);

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
    void Pro_플랜도_월_사용량을_카운트하며_한도_이내면_로그가_저장된다() {
        // given — PRO도 월 200회 한도가 있어 카운트를 확인한다
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(10L);

        // when
        aiUsageRecorder.checkAndRecord(store, OWNER_ID, AiPlanType.PRO, AiUsageType.MARKETING_DRAFT, YEAR_MONTH);

        // then
        verify(aiUsageLogRepository).countByStore_StoreIdAndYearMonth(anyLong(), anyString());
        verify(aiUsageLogRepository).save(any());
    }

    @Test
    void Pro_플랜_월_200회_초과_시_AI_USAGE_LIMIT_EXCEEDED_예외가_발생한다() {
        // given
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(200L);

        // when & then
        assertThatThrownBy(() -> aiUsageRecorder.checkAndRecord(
                store, OWNER_ID, AiPlanType.PRO, AiUsageType.MARKETING_DRAFT, YEAR_MONTH))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);
        verify(aiUsageLogRepository, never()).save(any());
    }

    @Test
    void Free_플랜_월_5회_초과_시_AI_USAGE_LIMIT_EXCEEDED_예외가_발생한다() {
        // given — FREE 플랜도 월 5회 체험 한도가 있어, 초과 시 거부된다
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(5L);

        // when & then
        assertThatThrownBy(() -> aiUsageRecorder.checkAndRecord(
                store, OWNER_ID, AiPlanType.FREE, AiUsageType.MARKETING_DRAFT, YEAR_MONTH))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);
        verify(aiUsageLogRepository, never()).save(any());
    }

    @Test
    void Free_플랜_한도_이내면_사용량_로그가_저장된다() {
        // given
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(2L);

        // when
        aiUsageRecorder.checkAndRecord(store, OWNER_ID, AiPlanType.FREE, AiUsageType.MARKETING_DRAFT, YEAR_MONTH);

        // then
        verify(aiUsageLogRepository).save(any());
    }

    @Test
    void Basic_플랜_49회_사용_시_50번째_요청은_성공한다() {
        // given
        Store store = stubStore();
        when(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(anyLong(), anyString())).thenReturn(49L);

        // when
        aiUsageRecorder.checkAndRecord(store, OWNER_ID, AiPlanType.BASIC, AiUsageType.MARKETING_DRAFT, YEAR_MONTH);

        // then
        verify(aiUsageLogRepository).save(any());
    }
}
