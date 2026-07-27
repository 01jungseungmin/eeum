package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanPaymentFailureRecorderTest {

    @InjectMocks
    private AiPlanPaymentFailureRecorder recorder;

    @Mock
    private AiPlanPaymentRepository aiPlanPaymentRepository;

    private static final String PAYMENT_ID = "portone-payment-1";

    @Test
    void 결제_내역이_있으면_FAILED로_마킹된다() {
        // given
        AiPlanPayment payment = AiPlanPayment.createPending(
                mock(Store.class), AiPlanType.BASIC, new BigDecimal("9900"), PAYMENT_ID);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        // when
        recorder.markFailed(PAYMENT_ID);

        // then
        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.FAILED);
        verify(aiPlanPaymentRepository).findByPortonePaymentId(PAYMENT_ID);
        verifyNoMoreInteractions(aiPlanPaymentRepository);
    }

    @Test
    void 결제_내역이_없으면_예외_없이_아무_일도_일어나지_않는다() {
        // given
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatCode(() -> recorder.markFailed(PAYMENT_ID)).doesNotThrowAnyException();
        verify(aiPlanPaymentRepository).findByPortonePaymentId(PAYMENT_ID);
        verifyNoMoreInteractions(aiPlanPaymentRepository);
    }
}
