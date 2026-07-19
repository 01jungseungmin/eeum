package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiChatMessageRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiChatResponseDto;
import com.eeum.eeum.application.ai.generator.TemplateAiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatbotServiceTest {

    @InjectMocks
    private AiChatbotService chatbotService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiChatMessageRecorder chatMessageRecorder;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private InquiryRepository inquiryRepository;
    @Spy private TemplateAiTextGenerator aiTextGenerator = new TemplateAiTextGenerator();

    private static final Long OWNER_ID = 100L;

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(1L);
        lenient().when(store.getName()).thenReturn("테스트 상점");
        lenient().when(store.getAccount()).thenReturn(mock(Account.class));
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 범위_밖_키워드_입력_시_안내_메시지를_반환하고_사용량은_카운트하지_않는다() {
        // given
        stubStore();

        // when
        AiChatResponseDto response = chatbotService.answer(
                OWNER_ID, new AiChatMessageRequestDto(null, "부가세 신고 어떻게 해요?"));

        // then
        assertThat(response.isOutOfScope()).isTrue();
        assertThat(response.getText()).contains("지원 범위 밖");
        assertThat(response.isUsageCounted()).isFalse();
        verify(supportService, never()).consumeGeneration(any(), any(), any(), any());
    }

    @Test
    void 챗봇_생성성_답변은_월_사용량에_카운트된다() {
        // given
        Store store = stubStore();

        // when — 고정 질문 2번: "오늘 공지 문구 써줘"
        AiChatResponseDto response = chatbotService.answer(
                OWNER_ID, new AiChatMessageRequestDto(2, null));

        // then
        assertThat(response.isUsageCounted()).isTrue();
        assertThat(response.getText()).isNotBlank();
        verify(supportService).consumeGeneration(store, OWNER_ID, AiFeature.CHATBOT_GENERATION, AiUsageType.CHATBOT_GENERATION);
    }

    @Test
    void 사용량_초과로_consumeGeneration이_실패하면_LLM은_호출되지_않는다() {
        // given — 플랜/한도 초과 사용자가 실제 LLM 비용을 발생시키기 전에 걸러져야 한다
        stubStore();
        doThrow(new BusinessException(ErrorCode.AI_USAGE_LIMIT_EXCEEDED))
                .when(supportService).consumeGeneration(any(), any(), any(), any());

        // when — 고정 질문 2번: "오늘 공지 문구 써줘"
        assertThatThrownBy(() -> chatbotService.answer(OWNER_ID, new AiChatMessageRequestDto(2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);

        // then
        verify(aiTextGenerator, never()).noticeCopy(any(), any(), any(), any());
    }

    @Test
    void 챗봇_조회성_답변은_사용량에_카운트되지_않는다() {
        // given
        stubStore();
        when(storeReviewRepository.findByStore_StoreIdAndCreatedAtAfter(anyLong(), any()))
                .thenReturn(List.of());

        // when — 고정 질문 3번: "우리 가게 리뷰 요약해줘"
        AiChatResponseDto response = chatbotService.answer(
                OWNER_ID, new AiChatMessageRequestDto(3, null));

        // then
        assertThat(response.isUsageCounted()).isFalse();
        verify(supportService, never()).consumeGeneration(any(), any(), any(), any());
    }

    @Test
    void 범위_안_자유_입력은_기본_응답과_액션_카드를_반환한다() {
        // given
        stubStore();

        // when
        AiChatResponseDto response = chatbotService.answer(
                OWNER_ID, new AiChatMessageRequestDto(null, "장사 잘 되게 도와줘"));

        // then
        assertThat(response.isOutOfScope()).isFalse();
        assertThat(response.getActions()).isNotEmpty();
    }

    @Test
    void 대화_이력은_USER와_ASSISTANT_역할로_각각_저장된다() {
        // given
        stubStore();

        // when
        chatbotService.answer(OWNER_ID, new AiChatMessageRequestDto(null, "장사 잘 되게 도와줘"));

        // then
        verify(chatMessageRecorder, times(2)).record(any(), any(), any(), any());
    }

    @Test
    void 고정_추천_질문은_8종이_조회된다() {
        // when
        var questions = chatbotService.getQuickQuestions();

        // then
        assertThat(questions).hasSize(8);
        assertThat(questions).extracting("question").contains("이번 주 이벤트 뭐 할까요?", "답글 초안 써줘");
    }

    @Test
    void 존재하지_않는_quickQuestionId는_AI_CHATBOT_INVALID_INPUT_예외가_발생한다() {
        // given
        stubStore();

        // when & then
        assertThatThrownBy(() -> chatbotService.answer(OWNER_ID, new AiChatMessageRequestDto(99, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_CHATBOT_INVALID_INPUT);
    }

    @Test
    void 빈_텍스트_입력은_AI_CHATBOT_INVALID_INPUT_예외가_발생한다() {
        // given
        stubStore();

        // when & then
        assertThatThrownBy(() -> chatbotService.answer(OWNER_ID, new AiChatMessageRequestDto(null, "  ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_CHATBOT_INVALID_INPUT);
    }
}
