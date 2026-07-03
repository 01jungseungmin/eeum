package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiMarketingDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiMarketingDraftResponseDto;
import com.eeum.eeum.application.ai.generator.AiText;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMarketingServiceTest {

    @InjectMocks
    private AiMarketingService aiMarketingService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiTextGenerator aiTextGenerator;
    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    private Store stubStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(store.getName()).thenReturn("테스트 가게");
        lenient().when(store.getAccount()).thenReturn(mock(Account.class));
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    @Test
    void SNS_CARD_채널로_공지_초안_생성_시_AI_INVALID_CHANNEL_예외가_발생한다() {
        // given
        stubStore();
        AiMarketingDraftRequestDto request = new AiMarketingDraftRequestDto(
                AiNoticeType.EVENT, AiTone.FRIENDLY, List.of(AiChannel.SNS_CARD), "키워드");

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
                AiNoticeType.EVENT, AiTone.FRIENDLY, List.of(AiChannel.APP_PUSH), "키워드");

        when(aiTextGenerator.marketingCopy(any(), any(), any(), any()))
                .thenReturn(new AiText("제목", "내용"));
        AiGeneratedMessage savedMessage = AiGeneratedMessage.createDraft(
                store, store.getAccount(), null, null, null, "제목", "내용", AiChannel.APP_PUSH);
        when(aiGeneratedMessageRepository.save(any())).thenReturn(savedMessage);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, STORE_ID))
                .thenReturn(List.of(1L, 2L, 3L));

        // when
        AiMarketingDraftResponseDto result = aiMarketingService.createMarketingDraft(OWNER_ID, request);

        // then
        assertThat(result.getTitle()).isEqualTo("제목");
        assertThat(result.getContent()).isEqualTo("내용");
        assertThat(result.getChannelReaches()).hasSize(1);
        assertThat(result.getEstimatedReach()).isEqualTo(3);
    }

    @Test
    void 도달_수가_0이면_sendable이_false이다() {
        // given
        Store store = stubStore();
        AiMarketingDraftRequestDto request = new AiMarketingDraftRequestDto(
                AiNoticeType.EVENT, AiTone.FRIENDLY, List.of(AiChannel.SNS_CARD), "키워드");

        when(aiTextGenerator.marketingCopy(any(), any(), any(), any()))
                .thenReturn(new AiText("제목", "내용"));
        AiGeneratedMessage savedMessage = AiGeneratedMessage.createDraft(
                store, store.getAccount(), null, null, null, "제목", "내용", AiChannel.SNS_CARD);
        when(aiGeneratedMessageRepository.save(any())).thenReturn(savedMessage);

        // when
        AiMarketingDraftResponseDto result = aiMarketingService.createMarketingDraft(OWNER_ID, request);

        // then
        assertThat(result.isSendable()).isFalse();
        assertThat(result.getEstimatedReach()).isZero();
    }
}
