package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanExpirationSchedulerTest {

    @InjectMocks
    private AiPlanExpirationScheduler scheduler;

    @Mock
    private AiPlanSubscriptionRepository aiPlanSubscriptionRepository;

    private AiPlanSubscription expiredSubscription() {
        return AiPlanSubscription.createWithPeriod(
                mock(Store.class), AiPlanType.BASIC,
                LocalDateTime.now().minusMonths(1), LocalDateTime.now().minusDays(1));
    }

    @Test
    void 만료일이_지난_활성_구독은_전부_비활성화된다() {
        // given
        AiPlanSubscription first = expiredSubscription();
        AiPlanSubscription second = expiredSubscription();
        when(aiPlanSubscriptionRepository.findByActiveTrueAndExpiredAtBeforeOrderByExpiredAtAscAiPlanSubscriptionIdAsc(any(), any()))
                .thenReturn(List.of(first, second));
        when(aiPlanSubscriptionRepository.findByActiveFalseAndStartedAtLessThanEqualAndExpiredAtAfterOrderByStartedAtAscAiPlanSubscriptionIdAsc(any(), any(), any()))
                .thenReturn(List.of());

        // when
        scheduler.expireSubscriptions();

        // then
        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isFalse();
        verify(aiPlanSubscriptionRepository)
                .findByActiveTrueAndExpiredAtBeforeOrderByExpiredAtAscAiPlanSubscriptionIdAsc(any(LocalDateTime.class), any());
        verify(aiPlanSubscriptionRepository)
                .findByActiveFalseAndStartedAtLessThanEqualAndExpiredAtAfterOrderByStartedAtAscAiPlanSubscriptionIdAsc(
                        any(LocalDateTime.class), any(LocalDateTime.class), any());
        verifyNoMoreInteractions(aiPlanSubscriptionRepository);
    }

    @Test
    void 만료된_구독이_없으면_아무_일도_하지_않는다() {
        // given
        when(aiPlanSubscriptionRepository.findByActiveTrueAndExpiredAtBeforeOrderByExpiredAtAscAiPlanSubscriptionIdAsc(any(), any()))
                .thenReturn(List.of());
        when(aiPlanSubscriptionRepository.findByActiveFalseAndStartedAtLessThanEqualAndExpiredAtAfterOrderByStartedAtAscAiPlanSubscriptionIdAsc(any(), any(), any()))
                .thenReturn(List.of());

        // when
        scheduler.expireSubscriptions();

        // then
        verify(aiPlanSubscriptionRepository)
                .findByActiveTrueAndExpiredAtBeforeOrderByExpiredAtAscAiPlanSubscriptionIdAsc(any(LocalDateTime.class), any());
        verify(aiPlanSubscriptionRepository)
                .findByActiveFalseAndStartedAtLessThanEqualAndExpiredAtAfterOrderByStartedAtAscAiPlanSubscriptionIdAsc(
                        any(LocalDateTime.class), any(LocalDateTime.class), any());
        verifyNoMoreInteractions(aiPlanSubscriptionRepository);
    }

    @Test
    void 시작_시각이_지난_예약_구독은_활성화된다() {
        // given — 상위 플랜 기간이 끝나 활성화를 기다리는 하위 플랜 예약 구독
        AiPlanSubscription reserved = AiPlanSubscription.createReserved(
                mock(Store.class), null, AiPlanType.BASIC,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMonths(1));
        when(aiPlanSubscriptionRepository.findByActiveTrueAndExpiredAtBeforeOrderByExpiredAtAscAiPlanSubscriptionIdAsc(any(), any()))
                .thenReturn(List.of());
        when(aiPlanSubscriptionRepository.findByActiveFalseAndStartedAtLessThanEqualAndExpiredAtAfterOrderByStartedAtAscAiPlanSubscriptionIdAsc(any(), any(), any()))
                .thenReturn(List.of(reserved));

        // when
        scheduler.expireSubscriptions();

        // then
        assertThat(reserved.isActive()).isTrue();
    }
}
