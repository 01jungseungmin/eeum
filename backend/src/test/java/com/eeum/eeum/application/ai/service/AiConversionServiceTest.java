package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiConversionEvent;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiConversionType;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiConversionEventRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConversionServiceTest {

    @InjectMocks
    private AiConversionService conversionService;

    @Mock private AiMessageDeliveryRepository aiMessageDeliveryRepository;
    @Mock private AiConversionEventRepository aiConversionEventRepository;

    private static final Long CUSTOMER_ID = 10L;
    private static final Long STORE_ID = 1L;
    private static final Long MESSAGE_ID = 5L;

    private AiMessageDelivery deliverySentAt(LocalDateTime sentAt) {
        Store store = mock(Store.class);
        Account owner = mock(Account.class);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.CUSTOMER_CARE, null, null, "제목", "본문", AiChannel.APP_PUSH);
        ReflectionTestUtils.setField(message, "aiGeneratedMessageId", MESSAGE_ID);
        AiMessageDelivery delivery = AiMessageDelivery.record(
                message, store, CUSTOMER_ID, AiChannel.APP_PUSH, AiDeliveryStatus.SENT, null, "fcm-id");
        ReflectionTestUtils.setField(delivery, "sentAt", sentAt);
        return delivery;
    }

    @Test
    void 메시지_발송_후_주문이_발생하면_전환이_기록된다() {
        // given — 3일 전 발송, 오늘 주문
        LocalDateTime now = LocalDateTime.now();
        when(aiMessageDeliveryRepository
                .findByTargetAccountIdAndStore_StoreIdAndStatusAndSentAtAfterOrderBySentAtDesc(
                        eq(CUSTOMER_ID), eq(STORE_ID), eq(AiDeliveryStatus.SENT), any()))
                .thenReturn(List.of(deliverySentAt(now.minusDays(3))));
        when(aiConversionEventRepository.existsByMessage_AiGeneratedMessageIdAndAccountId(MESSAGE_ID, CUSTOMER_ID))
                .thenReturn(false);

        // when
        conversionService.recordConversion(CUSTOMER_ID, STORE_ID, AiConversionType.ORDER, 77L, null, now);

        // then
        verify(aiConversionEventRepository).save(any(AiConversionEvent.class));
    }

    @Test
    void 메시지_발송_이전에_발생한_주문은_전환으로_기록하지_않는다() {
        // given — 발송 시각이 주문보다 나중
        LocalDateTime orderAt = LocalDateTime.now().minusDays(1);
        when(aiMessageDeliveryRepository
                .findByTargetAccountIdAndStore_StoreIdAndStatusAndSentAtAfterOrderBySentAtDesc(
                        anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(deliverySentAt(LocalDateTime.now())));

        // when
        conversionService.recordConversion(CUSTOMER_ID, STORE_ID, AiConversionType.ORDER, 77L, null, orderAt);

        // then
        verify(aiConversionEventRepository, never()).save(any());
    }

    @Test
    void 발송_이력이_없으면_전환으로_기록하지_않는다() {
        // given — 7일 window 내 발송 없음
        when(aiMessageDeliveryRepository
                .findByTargetAccountIdAndStore_StoreIdAndStatusAndSentAtAfterOrderBySentAtDesc(
                        anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());

        // when
        conversionService.recordConversion(CUSTOMER_ID, STORE_ID, AiConversionType.ORDER, 77L, null, LocalDateTime.now());

        // then
        verify(aiConversionEventRepository, never()).save(any());
    }

    @Test
    void 같은_메시지에_대한_같은_고객의_중복_전환은_기록하지_않는다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        when(aiMessageDeliveryRepository
                .findByTargetAccountIdAndStore_StoreIdAndStatusAndSentAtAfterOrderBySentAtDesc(
                        anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(deliverySentAt(now.minusDays(1))));
        when(aiConversionEventRepository.existsByMessage_AiGeneratedMessageIdAndAccountId(MESSAGE_ID, CUSTOMER_ID))
                .thenReturn(true);

        // when
        conversionService.recordConversion(CUSTOMER_ID, STORE_ID, AiConversionType.ORDER, 78L, null, now);

        // then
        verify(aiConversionEventRepository, never()).save(any());
    }
}
