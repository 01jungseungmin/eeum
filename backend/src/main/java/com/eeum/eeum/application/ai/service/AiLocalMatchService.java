package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiLocalMatchConditionRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiExposureStatusResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiLocalMatchResponseDto;
import com.eeum.eeum.application.ai.generator.AiInsightGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiLocalMatchService {

    private static final Duration EXPOSURE_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiManagerSupportService supportService;
    private final AiInsightGenerator aiInsightGenerator;
    private final AiExposureStatusRepository aiExposureStatusRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final RedisLockService redisLockService;

    @Transactional(readOnly = true)
    public AiLocalMatchResponseDto getLocalMatch(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_VIEW);
        AiExposureStatus status = aiExposureStatusRepository.findByStore_StoreId(store.getStoreId())
                .orElseGet(() -> AiExposureStatus.init(store));
        return buildMatchResponse(store, status);
    }

    @Transactional
    public AiLocalMatchResponseDto updateConditions(Long ownerId, AiLocalMatchConditionRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_VIEW);

        // start/stop 과 동일한 락 키로 직렬화. flush()로 락 해제 전 DB 반영 —
        // 락 해제 후 @Transactional 커밋 전까지 생기는 가시성 갭을 제거한다
        return redisLockService.executeWithLock(LockKeys.aiExposure(store.getStoreId()), EXPOSURE_LOCK_LEASE, () -> {
            AiExposureStatus status = getOrCreateStatus(store);
            int targetCount = estimateTargetCount(store.getStoreId(),
                    request.getCustomerType() != null ? request.getCustomerType() : status.getCustomerType());
            status.updateConditions(request.getRadiusKm(), request.getInterest(), request.getCustomerType(), targetCount);
            aiExposureStatusRepository.flush();
            return buildMatchResponse(store, status);
        });
    }

    // 노출 시작 — 이미 진행 중이면 AI_INVALID_STATUS. 1차에서는 실제 광고 집행 없이 상태/로그만 기록
    @Transactional
    public AiExposureStatusResponseDto startExposure(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_EXPOSURE);

        return redisLockService.executeWithLock(LockKeys.aiExposure(store.getStoreId()), EXPOSURE_LOCK_LEASE, () -> {
            AiExposureStatus status = getOrCreateStatus(store);
            int targetCount = estimateTargetCount(store.getStoreId(), status.getCustomerType());
            status.start(LocalDateTime.now(), targetCount);
            aiActionLogRepository.save(AiActionLog.record(
                    store, store.getAccount(), AiActionType.EXPOSURE_STARTED,
                    "AI_EXPOSURE", status.getAiExposureStatusId(), "생활권 매칭 노출 시작"));
            aiExposureStatusRepository.flush();
            return AiExposureStatusResponseDto.from(status);
        });
    }

    @Transactional(readOnly = true)
    public AiExposureStatusResponseDto getExposureStatus(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_VIEW);
        AiExposureStatus status = aiExposureStatusRepository.findByStore_StoreId(store.getStoreId())
                .orElseGet(() -> AiExposureStatus.init(store));
        return AiExposureStatusResponseDto.from(status);
    }

    @Transactional
    public AiExposureStatusResponseDto stopExposure(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.LOCAL_MATCH_EXPOSURE);

        return redisLockService.executeWithLock(LockKeys.aiExposure(store.getStoreId()), EXPOSURE_LOCK_LEASE, () -> {
            AiExposureStatus status = getOrCreateStatus(store);
            status.stop(LocalDateTime.now());
            aiActionLogRepository.save(AiActionLog.record(
                    store, store.getAccount(), AiActionType.EXPOSURE_STOPPED,
                    "AI_EXPOSURE", status.getAiExposureStatusId(), "생활권 매칭 노출 중지"));
            aiExposureStatusRepository.flush();
            return AiExposureStatusResponseDto.from(status);
        });
    }

    // ===================== 내부 집계 =====================

    private AiExposureStatus getOrCreateStatus(Store store) {
        return aiExposureStatusRepository.findByStore_StoreId(store.getStoreId())
                .orElseGet(() -> aiExposureStatusRepository.save(AiExposureStatus.init(store)));
    }

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
        int targetCount = estimateTargetCount(storeId, status.getCustomerType());

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

    private int estimateTargetCount(Long storeId, AiCustomerType customerType) {
        Set<Long> orderAccountIds = new HashSet<>(
                orderRepository.findOrderAccountIdsByStoreIdAndStatus(storeId, OrderStatus.COMPLETED));
        Set<Long> favoriteAccountIds = new HashSet<>(
                favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, storeId));

        Set<Long> allCustomers = new HashSet<>();
        allCustomers.addAll(orderAccountIds);
        allCustomers.addAll(favoriteAccountIds);

        return switch (customerType) {
            case ALL -> allCustomers.size();
            case REGULAR -> countRegulars(storeId);
            case NEW -> Math.max(0, allCustomers.size() - orderAccountIds.size()); // 주문 이력 없는 잠재 고객
        };
    }

    private int countRegulars(Long storeId) {
        List<Order> completedOrders = orderRepository.findByStore_StoreIdAndStatus(storeId, OrderStatus.COMPLETED);
        Map<Long, Long> countsByAccount = completedOrders.stream()
                .collect(Collectors.groupingBy(order -> order.getAccount().getAccountId(), Collectors.counting()));
        return (int) countsByAccount.values().stream().filter(count -> count >= 3).count();
    }

    private int ratio(int part, int total) {
        return total == 0 ? 0 : Math.min(100, part * 100 / total);
    }
}
