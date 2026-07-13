package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiActivitySummaryResponseDto;
import com.eeum.eeum.application.ai.generator.TemplateAiInsightGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiConversionEventRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiActivityServiceTest {

    @InjectMocks
    private AiActivityService aiActivityService;

    @Mock private AiManagerSupportService supportService;
    @Spy private TemplateAiInsightGenerator aiInsightGenerator = new TemplateAiInsightGenerator();
    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiConversionEventRepository aiConversionEventRepository;
    @Mock private InquiryRepository inquiryRepository;

    private static final Long STORE_ID = 1L;
    private static final Long OWNER_ID = 100L;

    private Store stubStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getName()).thenReturn("테스트 가게");
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    private AiGeneratedMessage stubMessage(AiMessageType type, AiMessageStatus status, String targetType) {
        Account account = mock(Account.class);
        Store store = mock(Store.class);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, account, type, targetType, null, "제목", "내용", AiChannel.APP_PUSH);
        if (status == AiMessageStatus.SENT) {
            message.edit("제목", "내용"); // REVIEWED 상태로 전환
            message.send(LocalDateTime.now());
        }
        return message;
    }

    @Test
    void SENT_상태_메시지만_발송_수로_집계된다() {
        // given
        Store store = stubStore();
        AiGeneratedMessage sent = stubMessage(AiMessageType.CUSTOMER_CARE, AiMessageStatus.SENT, null);
        AiGeneratedMessage draft = stubMessage(AiMessageType.CUSTOMER_CARE, AiMessageStatus.DRAFT, null);
        when(aiGeneratedMessageRepository.findByStore_StoreIdAndCreatedAtAfter(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of(sent, draft));
        when(inquiryRepository.countByStore_StoreIdAndStatus(anyLong(), eq(InquiryStatus.PENDING))).thenReturn(0L);
        when(aiInsightGenerator.activityHighlight(any(), anyLong(), anyLong())).thenReturn("하이라이트");

        // when
        AiActivitySummaryResponseDto result = aiActivityService.getActivitySummary(OWNER_ID);

        // then
        assertThat(result.getSentCount()).isEqualTo(1);
    }

    @Test
    void INACTIVE_REGULAR_타겟은_이탈_알림으로_분류된다() {
        // given
        Store store = stubStore();
        AiGeneratedMessage inactive = stubMessage(AiMessageType.CUSTOMER_CARE, AiMessageStatus.DRAFT,
                AiCareType.INACTIVE_REGULAR.name());
        AiGeneratedMessage regular = stubMessage(AiMessageType.CUSTOMER_CARE, AiMessageStatus.DRAFT,
                AiCareType.CART_INTEREST.name());
        when(aiGeneratedMessageRepository.findByStore_StoreIdAndCreatedAtAfter(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of(inactive, regular));
        when(inquiryRepository.countByStore_StoreIdAndStatus(anyLong(), eq(InquiryStatus.PENDING))).thenReturn(0L);
        when(aiInsightGenerator.activityHighlight(any(), anyLong(), anyLong())).thenReturn("하이라이트");

        // when
        AiActivitySummaryResponseDto result = aiActivityService.getActivitySummary(OWNER_ID);

        // then
        assertThat(result.getInactiveAlertCount()).isEqualTo(1);
        assertThat(result.getRegularMessageCount()).isEqualTo(1);
    }

    @Test
    void 메시지_타입별_초안_수가_올바르게_집계된다() {
        // given
        Store store = stubStore();
        List<AiGeneratedMessage> messages = List.of(
                stubMessage(AiMessageType.REVIEW_REPLY, AiMessageStatus.DRAFT, null),
                stubMessage(AiMessageType.REVIEW_REPLY, AiMessageStatus.DRAFT, null),
                stubMessage(AiMessageType.INQUIRY_REPLY, AiMessageStatus.DRAFT, null)
        );
        when(aiGeneratedMessageRepository.findByStore_StoreIdAndCreatedAtAfter(anyLong(), any(LocalDateTime.class)))
                .thenReturn(messages);
        when(inquiryRepository.countByStore_StoreIdAndStatus(anyLong(), eq(InquiryStatus.PENDING))).thenReturn(2L);
        when(aiInsightGenerator.activityHighlight(any(), anyLong(), anyLong())).thenReturn("하이라이트");

        // when
        AiActivitySummaryResponseDto result = aiActivityService.getActivitySummary(OWNER_ID);

        // then
        assertThat(result.getReviewReplyDraftCount()).isEqualTo(2);
        assertThat(result.getInquiryReplyDraftCount()).isEqualTo(1);
        assertThat(result.getUnansweredInquiryRemainingCount()).isEqualTo(2);
        assertThat(result.getSentCount()).isZero();
        assertThat(result.getDraftCount()).isEqualTo(3);
    }

    @Test
    void draftCount는_COMPLAINT_REPLY_포함_전체_메시지_수를_반영한다() {
        // given — COMPLAINT_REPLY 타입은 4개 서브타입 합산에 없으므로 draftCount와 불일치가 없어야 함
        Store store = stubStore();
        List<AiGeneratedMessage> messages = List.of(
                stubMessage(AiMessageType.REVIEW_REPLY, AiMessageStatus.DRAFT, null),
                stubMessage(AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT, null)
        );
        when(aiGeneratedMessageRepository.findByStore_StoreIdAndCreatedAtAfter(anyLong(), any(LocalDateTime.class)))
                .thenReturn(messages);
        when(inquiryRepository.countByStore_StoreIdAndStatus(anyLong(), eq(InquiryStatus.PENDING))).thenReturn(0L);
        when(aiInsightGenerator.activityHighlight(any(), anyLong(), anyLong())).thenReturn("하이라이트");

        // when
        AiActivitySummaryResponseDto result = aiActivityService.getActivitySummary(OWNER_ID);

        // then — draftCount는 2 (전체), reviewReplyDraftCount는 1
        assertThat(result.getDraftCount()).isEqualTo(2);
        assertThat(result.getReviewReplyDraftCount()).isEqualTo(1);
    }
}
