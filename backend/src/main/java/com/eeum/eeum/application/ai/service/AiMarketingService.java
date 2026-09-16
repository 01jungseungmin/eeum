package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiMarketingDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiMarketingDraftResponseDto;
import com.eeum.eeum.application.ai.dto.response.ChannelReachDto;
import com.eeum.eeum.application.ai.generator.AiText;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiMarketingService {

    private final AiManagerSupportService supportService;
    private final AiTextGenerator aiTextGenerator;
    private final AiDraftPersistenceExecutor draftPersistenceExecutor;
    private final FavoriteRepository favoriteRepository;
    private final OrderRepository orderRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    // 마케팅 개요 — 채널별 도달 추정치 (문구 생성 전 미리보기용)
    @Transactional(readOnly = true)
    public List<ChannelReachDto> getChannelReaches(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.MARKETING_VIEW);
        return estimateReaches(store.getStoreId(), List.of(AiChannel.values()));
    }

    public AiMarketingDraftResponseDto createMarketingDraft(Long ownerId, AiMarketingDraftRequestDto request) {
        return createDraft(ownerId, request, AiMessageType.EVENT_MARKETING,
                AiFeature.MARKETING_DRAFT, AiUsageType.MARKETING_DRAFT, false);
    }

    // 공지 등록 화면용 초안 — SNS_CARD는 발송 채널로 사용 불가
    public AiMarketingDraftResponseDto createNoticeDraft(Long ownerId, AiMarketingDraftRequestDto request) {
        return createDraft(ownerId, request, AiMessageType.NOTICE,
                AiFeature.NOTICE_DRAFT, AiUsageType.NOTICE_DRAFT, true);
    }

    // @Transactional을 두지 않는다 — LLM 호출(외부 HTTP)이 DB 트랜잭션을 오래 붙잡지 않도록,
    // 생성은 트랜잭션 밖에서 하고 저장만 draftPersistenceExecutor의 짧은 트랜잭션에 위임한다.
    private AiMarketingDraftResponseDto createDraft(
            Long ownerId,
            AiMarketingDraftRequestDto request,
            AiMessageType messageType,
            AiFeature feature,
            AiUsageType usageType,
            boolean noticeChannelOnly
    ) {
        Store store = supportService.getOwnerStore(ownerId);
        if (noticeChannelOnly && request.getChannels().stream().anyMatch(channel -> !channel.isNoticeSendable())) {
            throw new BusinessException(ErrorCode.AI_INVALID_CHANNEL);
        }
        supportService.enforceDraftCapacity(store, messageType, request.isConfirmDelete());

        // 생성 시도 = 사용량 차감 — 플랜/한도 초과 사용자가 LLM 호출(비용 발생) 전에 걸러지도록 먼저 차감한다
        supportService.consumeGeneration(store, ownerId, feature, usageType);
        AiText text = messageType == AiMessageType.NOTICE
                ? aiTextGenerator.noticeCopy(store.getName(), request.getNoticeType(), request.getTone(), request.getKeyword())
                : aiTextGenerator.marketingCopy(store.getName(), request.getNoticeType(), request.getTone(), request.getKeyword());

        // 대표 채널 하나를 메시지에 저장 — 발송 시 채널별 분기는 2차에서 처리
        AiChannel primaryChannel = request.getChannels().get(0);
        AiGeneratedMessage message = draftPersistenceExecutor.saveDraftInTx(
                store, store.getAccount(), messageType, request.getNoticeType().name(), null,
                text.title(), text.content(), primaryChannel, messageType.name(),
                messageType == AiMessageType.NOTICE ? "공지 문구 초안 생성" : "마케팅 문구 초안 생성");
        supportService.healDraftCapacity(store, messageType);

        List<ChannelReachDto> reaches =
                estimateReaches(store.getStoreId(), request.getChannels());
        long totalReach = reaches.stream().mapToLong(ChannelReachDto::getEstimatedReach).sum();

        return AiMarketingDraftResponseDto.builder()
                .messageId(message.getAiGeneratedMessageId())
                .title(text.title())
                .content(text.content())
                .estimatedReach(totalReach)
                .selectedChannelCount(request.getChannels().size())
                .characterCount(text.content() != null ? text.content().length() : 0)
                .sendable(totalReach > 0)
                .channelReaches(reaches)
                .build();
    }

    // 채널별 도달 추정 — 실제 데이터 기반, 없으면 0
    private List<ChannelReachDto> estimateReaches(Long storeId, List<AiChannel> channels) {
        return channels.stream()
                .distinct()
                .map(channel -> ChannelReachDto.builder()
                        .channel(channel)
                        .estimatedReach(estimateReach(storeId, channel))
                        .build())
                .toList();
    }

    private long estimateReach(Long storeId, AiChannel channel) {
        return switch (channel) {
            // 앱 푸시 — 상점 찜 고객 수
            case APP_PUSH -> favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, storeId).size();
            // 알림톡 — 주문 이력이 있는 고객 수
            case KAKAO_ALERT -> orderRepository.findOrderAccountIdsByStoreIdAndStatus(storeId, OrderStatus.COMPLETED).size();
            // 상점 공지 — 상점 채팅 참여 고객 수
            case STORE_NOTICE -> chatParticipantRepository.findActiveParticipantAccountIdsByStoreRefId(
                    storeId, ChatRoomRefType.STORE, ParticipantStatus.ACTIVE).size();
            // SNS 카드 — 외부 채널이므로 1차에서는 추정 불가
            case SNS_CARD -> 0L;
        };
    }
}
