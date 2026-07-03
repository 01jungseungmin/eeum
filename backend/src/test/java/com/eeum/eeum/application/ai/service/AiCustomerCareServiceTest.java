package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiCustomerCareCardDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.generator.TemplateAiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCustomerCareServiceTest {

    @InjectMocks
    private AiCustomerCareService customerCareService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private CartRepository cartRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private InquiryRepository inquiryRepository;
    @Spy private TemplateAiTextGenerator aiTextGenerator = new TemplateAiTextGenerator();

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(store.getName()).thenReturn("테스트 상점");
        lenient().when(store.getAccount()).thenReturn(mock(Account.class));
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 고객_케어_카드_3종_조회에_성공한다() {
        // given
        stubStore();
        when(cartRepository.countByStore_StoreId(STORE_ID)).thenReturn(3L);
        when(orderRepository.findInactiveRegularAccountIds(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        when(inquiryRepository.countByStore_StoreIdAndStatus(STORE_ID, InquiryStatus.PENDING)).thenReturn(2L);
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatusAndSentAtAfter(
                anyLong(), any(), any(), any())).thenReturn(0L);

        // when
        List<AiCustomerCareCardDto> cards = customerCareService.getCareCards(OWNER_ID);

        // then
        assertThat(cards).hasSize(3);
        AiCustomerCareCardDto cartCard = cards.get(0);
        assertThat(cartCard.getCareType()).isEqualTo(AiCareType.CART_INTEREST);
        assertThat(cartCard.getTargetCustomerCount()).isEqualTo(3);
        assertThat(cartCard.isSendable()).isTrue();
        assertThat(cartCard.getPreparedMessage()).isNotBlank();
    }

    @Test
    void 데이터가_없으면_대상_0명과_발송_불가로_응답한다() {
        // given
        stubStore();
        when(cartRepository.countByStore_StoreId(STORE_ID)).thenReturn(0L);
        when(orderRepository.findInactiveRegularAccountIds(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        when(inquiryRepository.countByStore_StoreIdAndStatus(STORE_ID, InquiryStatus.PENDING)).thenReturn(0L);
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatusAndSentAtAfter(
                anyLong(), any(), any(), any())).thenReturn(0L);

        // when
        List<AiCustomerCareCardDto> cards = customerCareService.getCareCards(OWNER_ID);

        // then
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.getTargetCustomerCount()).isZero();
            assertThat(card.isSendable()).isFalse();
        });
    }

    @Test
    void 고객_케어_초안_생성에_성공하면_DRAFT_상태로_저장된다() {
        // given
        Store store = stubStore();
        when(aiGeneratedMessageRepository.save(any(AiGeneratedMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AiGeneratedMessageResponseDto response =
                customerCareService.createDraft(OWNER_ID, AiCareType.CART_INTEREST, null);

        // then
        assertThat(response.getType()).isEqualTo(AiMessageType.CUSTOMER_CARE);
        assertThat(response.getStatus()).isEqualTo(AiMessageStatus.DRAFT);
        assertThat(response.getContent()).isNotBlank();
        verify(supportService).consumeGeneration(store, OWNER_ID, AiFeature.CUSTOMER_CARE_DRAFT, AiUsageType.CUSTOMER_CARE_DRAFT);
        verify(aiActionLogRepository).save(any());
    }

    @Test
    void 플랜_미달_사장이_초안_생성_시_AI_PLAN_REQUIRED_예외가_전파된다() {
        // given
        Store store = stubStore();
        doThrow(new BusinessException(ErrorCode.AI_PLAN_REQUIRED))
                .when(supportService).consumeGeneration(store, OWNER_ID, AiFeature.CUSTOMER_CARE_DRAFT, AiUsageType.CUSTOMER_CARE_DRAFT);

        // when & then
        assertThatThrownBy(() -> customerCareService.createDraft(OWNER_ID, AiCareType.CART_INTEREST, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_REQUIRED);
    }
}
