package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiMarketingDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiMarketingDraftResponseDto;
import com.eeum.eeum.application.ai.generator.AiText;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMarketingServiceTest {

    @InjectMocks
    private AiMarketingService aiMarketingService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiTextGenerator aiTextGenerator;
    @Mock private AiDraftPersistenceExecutor draftPersistenceExecutor;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(store.getName()).thenReturn("테스트 가게");
        lenient().when(store.getAccount()).thenReturn(mock(Account.class));
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    @Test
    void SNS_CARD_채널로_공지_초안_생성_시_AI_INVALID_CHANNEL_예외가_발생한다() {
        // given
        Store store = mock(Store.class);
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        AiMarketingDraftRequestDto request = new AiMarketingDraftRequestDto(
                AiNoticeType.EVENT, AiTone.FRIENDLY, List.of(AiChannel.SNS_CARD), "키워드", false);

        // when & then
        assertThatThrownBy(() -> aiMarketingService.createNoticeDraft(OWNER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_CHANNEL);
    }

    @Test
    void 마케팅_초안_생성_시_채널별_도달_수가_포함된_응답을_반환한다() {
        // given
        Store store = stubStore();
        AiMarketingDraftRequestDto request = new AiMarketingDraftRequestDto(
                AiNoticeType.EVENT, AiTone.FRIENDLY, List.of(AiChannel.APP_PUSH), "키워드", false);

        when(aiTextGenerator.marketingCopy(any(), any(), any(), any()))
                .thenReturn(new AiText("제목", "내용"));
        AiGeneratedMessage savedMessage = AiGeneratedMessage.createDraft(
                store, store.getAccount(), AiMessageType.EVENT_MARKETING, null, null, "제목", "내용", AiChannel.APP_PUSH);
        when(draftPersistenceExecutor.saveDraftInTx(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(savedMessage);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, STORE_ID))
                .thenReturn(List.of(1L, 2L, 3L));

        // when
        AiMarketingDraftResponseDto result = aiMarketingService.createMarketingDraft(OWNER_ID, request);

        // then
        assertThat(result.getTitle()).isEqualTo("제목");
        assertThat(result.getContent()).isEqualTo("내용");
        assertThat(result.getChannelReaches()).hasSize(1);
        assertThat(result.getEstimatedReach()).isEqualTo(3);
        // 저장 성공 후에만 자기치유 퇴거를 호출해야 한다 (M-2: 미리 퇴거하면 생성 실패 시 데이터 손실)
        verify(supportService).healDraftCapacity(store, AiMessageType.EVENT_MARKETING);
    }

    @Test
    void 사용량_초과로_consumeGeneration이_실패하면_LLM은_호출되지_않는다() {
        // given — 플랜/한도 초과 사용자가 실제 LLM 비용을 발생시키기 전에 걸러져야 한다
        stubStore();
        AiMarketingDraftRequestDto request = new AiMarketingDraftRequestDto(
                AiNoticeType.EVENT, AiTone.FRIENDLY, List.of(AiChannel.APP_PUSH), "키워드", false);
        doThrow(new BusinessException(ErrorCode.AI_USAGE_LIMIT_EXCEEDED))
                .when(supportService).consumeGeneration(any(), any(), any(), any());

        // when & then
        assertThatThrownBy(() -> aiMarketingService.createMarketingDraft(OWNER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);
        verify(aiTextGenerator, never()).marketingCopy(any(), any(), any(), any());
        verify(draftPersistenceExecutor, never()).saveDraftInTx(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 도달_수가_0이면_sendable이_false이다() {
        // given
        Store store = stubStore();
        AiMarketingDraftRequestDto request = new AiMarketingDraftRequestDto(
                AiNoticeType.EVENT, AiTone.FRIENDLY, List.of(AiChannel.SNS_CARD), "키워드", false);

        when(aiTextGenerator.marketingCopy(any(), any(), any(), any()))
                .thenReturn(new AiText("제목", "내용"));
        AiGeneratedMessage savedMessage = AiGeneratedMessage.createDraft(
                store, store.getAccount(), AiMessageType.EVENT_MARKETING, null, null, "제목", "내용", AiChannel.SNS_CARD);
        when(draftPersistenceExecutor.saveDraftInTx(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(savedMessage);

        // when
        AiMarketingDraftResponseDto result = aiMarketingService.createMarketingDraft(OWNER_ID, request);

        // then
        assertThat(result.isSendable()).isFalse();
        assertThat(result.getEstimatedReach()).isZero();
    }
}
