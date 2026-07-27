package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiLocalMatchConditionRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiExposureStatusResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiLocalMatchResponseDto;
import com.eeum.eeum.application.ai.generator.TemplateAiInsightGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiLocalMatchService {

    private static final Duration EXPOSURE_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiManagerSupportService supportService;
    // 대시보드/분석용 텍스트는 항상 템플릿만 사용 — 조회(GET) API에서 실제 LLM(Gemini 등)이 호출되는 것을 방지한다.
    private final TemplateAiInsightGenerator aiInsightGenerator;
    private final AiExposureStatusRepository aiExposureStatusRepository;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final RedisLockService redisLockService;
    private final AiExposureCommandExecutor exposureCommandExecutor;

    @Transactional(readOnly = true)
    public AiLocalMatchResponseDto getLocalMatch(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_VIEW);
        AiExposureStatus status = aiExposureStatusRepository.findByStore_StoreId(store.getStoreId())
                .orElseGet(() -> AiExposureStatus.init(store));
        return buildMatchResponse(store, status);
    }

    // 락 먼저 잡고 → Executor에서 @Transactional 시작 → TX 커밋 후 락 해제.
    // buildMatchResponse는 이미 커밋된 데이터를 읽는 순수 조회 — 락 밖에서 실행해 리스 소진 방지.
    public AiLocalMatchResponseDto updateConditions(Long ownerId, AiLocalMatchConditionRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_VIEW);

        AiExposureStatus status = redisLockService.executeWithLock(
                LockKeys.aiExposure(store.getStoreId()), EXPOSURE_LOCK_LEASE,
                () -> exposureCommandExecutor.updateConditionsInTx(store, request));
        return buildMatchResponse(store, status);
    }

    // 노출 시작 — 이미 진행 중이면 AI_INVALID_STATUS. 1차에서는 실제 광고 집행 없이 상태/로그만 기록
    public AiExposureStatusResponseDto startExposure(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_EXPOSURE);

        return redisLockService.executeWithLock(LockKeys.aiExposure(store.getStoreId()), EXPOSURE_LOCK_LEASE, () ->
                AiExposureStatusResponseDto.from(exposureCommandExecutor.startExposureInTx(store, ownerId)));
    }

    @Transactional(readOnly = true)
    public AiExposureStatusResponseDto getExposureStatus(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_VIEW);
        AiExposureStatus status = aiExposureStatusRepository.findByStore_StoreId(store.getStoreId())
                .orElseGet(() -> AiExposureStatus.init(store));
        return AiExposureStatusResponseDto.from(status);
    }

    public AiExposureStatusResponseDto stopExposure(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_EXPOSURE);

        return redisLockService.executeWithLock(LockKeys.aiExposure(store.getStoreId()), EXPOSURE_LOCK_LEASE, () ->
                AiExposureStatusResponseDto.from(exposureCommandExecutor.stopExposureInTx(store, ownerId)));
    }

    // ===================== 내부 집계 =====================

    private AiLocalMatchResponseDto buildMatchResponse(Store store, AiExposureStatus status) {
        Long storeId = store.getStoreId();

        Set<Long> orderAccountIds = new HashSet<>(
                orderRepository.findOrderAccountIdsByStoreIdAndStatus(storeId, OrderStatus.COMPLETED));
        Set<Long> favoriteAccountIds = new HashSet<>(
                favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, storeId));
        Set<Long> chatAccountIds = new HashSet<>(
                chatParticipantRepository.findActiveParticipantAccountIdsByStoreRefId(
                        storeId, ChatRoomRefType.STORE, ParticipantStatus.ACTIVE));

        Set<Long> allCustomers = new HashSet<>();
        allCustomers.addAll(orderAccountIds);
        allCustomers.addAll(favoriteAccountIds);
        allCustomers.addAll(chatAccountIds);

        if (allCustomers.isEmpty()) {
            return AiLocalMatchResponseDto.builder()
                    .segments(List.of())
                    .matchReason(aiInsightGenerator.localMatchReason(
                            store.getName(), status.getInterest(), status.getRadiusKm(), 0))
                    .estimatedTargetCount(0)
                    .radiusKm(status.getRadiusKm())
                    .interest(status.getInterest())
                    .customerType(status.getCustomerType())
                    .hasData(false)
                    .emptyMessage("아직 매칭할 고객 데이터가 없습니다.")
                    .build();
        }

        int regularCount = countRegulars(storeId);
        int total = allCustomers.size();
        int interestMatchRate = ratio(favoriteAccountIds.size(), total);
        int regionMatchRate = ratio(orderAccountIds.size(), total); // 주문 고객 = 실제 생활권 방문 고객으로 간주
        int regularCustomerRatio = ratio(regularCount, total);
        int eventFitScore = ratio(chatAccountIds.size(), total);
        int totalScore = (interestMatchRate + regionMatchRate + regularCustomerRatio + eventFitScore) / 4;
        // 이미 조회한 set 재활용 — estimateTargetCount 내부 중복 쿼리 제거. chat 포함해 ALL/NEW 집계 정확도 보장.
        int targetCount = estimateTargetCount(storeId, status.getCustomerType(), orderAccountIds, favoriteAccountIds, chatAccountIds);

        List<String> segments = new ArrayList<>();
        if (!favoriteAccountIds.isEmpty()) {
            segments.add("우리 가게 찜 고객 " + favoriteAccountIds.size() + "명");
        }
        if (regularCount > 0) {
            segments.add("우리 가게 단골 " + regularCount + "명");
        }
        if (!orderAccountIds.isEmpty()) {
            segments.add("반경 " + status.getRadiusKm() + "km 주문 이력 고객 " + orderAccountIds.size() + "명");
        }
        if (!chatAccountIds.isEmpty()) {
            segments.add("채팅 문의 반응 고객 " + chatAccountIds.size() + "명");
        }

        return AiLocalMatchResponseDto.builder()
                .totalScore(totalScore)
                .regionMatchRate(regionMatchRate)
                .interestMatchRate(interestMatchRate)
                .eventFitScore(eventFitScore)
                .regularCustomerRatio(regularCustomerRatio)
                .segments(segments)
                .matchReason(aiInsightGenerator.localMatchReason(
                        store.getName(), status.getInterest(), status.getRadiusKm(), targetCount))
                .estimatedTargetCount(targetCount)
                .radiusKm(status.getRadiusKm())
                .interest(status.getInterest())
                .customerType(status.getCustomerType())
                .hasData(true)
                .emptyMessage(null)
                .build();
    }

    // 이미 조회한 set을 받아 중복 쿼리 제거. chatAccountIds 포함해 ALL/NEW 집계와 ratio 분모를 일치시킴.
    private int estimateTargetCount(Long storeId, AiCustomerType customerType,
            Set<Long> orderAccountIds, Set<Long> favoriteAccountIds, Set<Long> chatAccountIds) {
        Set<Long> allCustomers = new HashSet<>();
        allCustomers.addAll(orderAccountIds);
        allCustomers.addAll(favoriteAccountIds);
        allCustomers.addAll(chatAccountIds);

        return switch (customerType) {
            case ALL -> allCustomers.size();
            case REGULAR -> countRegulars(storeId);
            case NEW -> Math.max(0, allCustomers.size() - orderAccountIds.size());
        };
    }

    private int countRegulars(Long storeId) {
        return (int) orderRepository.countRegularAccounts(storeId, OrderStatus.COMPLETED, 3);
    }

    private int ratio(int part, int total) {
        return total == 0 ? 0 : Math.min(100, part * 100 / total);
    }
}
