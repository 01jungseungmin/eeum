package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiLocalMatchConditionRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
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
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 생활권 매칭 노출 상태 변경 — @Transactional이 Redis 락 안에서 시작되도록 별도 Bean으로 분리.
 * AiLocalMatchService가 락을 먼저 잡고, 이 Executor의 메서드를 호출해 TX를 시작/커밋한다.
 */
@Service
@RequiredArgsConstructor
public class AiExposureCommandExecutor {

    private final AiExposureStatusRepository aiExposureStatusRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public AiExposureStatus startExposureInTx(Store store, Long ownerAccountId) {
        AiExposureStatus status = getOrCreateStatus(store);
        int targetCount = estimateTargetCount(store.getStoreId(), status.getCustomerType());
        status.start(LocalDateTime.now(), targetCount);
        Account owner = accountRepository.getReferenceById(ownerAccountId);
        aiActionLogRepository.save(AiActionLog.record(
                store, owner, AiActionType.EXPOSURE_STARTED,
                "AI_EXPOSURE", status.getAiExposureStatusId(), "생활권 매칭 노출 시작"));
        return status;
    }

    @Transactional
    public AiExposureStatus stopExposureInTx(Store store, Long ownerAccountId) {
        AiExposureStatus status = aiExposureStatusRepository.findByStore_StoreId(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_EXPOSURE_NOT_FOUND));
        status.stop(LocalDateTime.now());
        Account owner = accountRepository.getReferenceById(ownerAccountId);
        aiActionLogRepository.save(AiActionLog.record(
                store, owner, AiActionType.EXPOSURE_STOPPED,
                "AI_EXPOSURE", status.getAiExposureStatusId(), "생활권 매칭 노출 중지"));
        return status;
    }

    @Transactional
    public AiExposureStatus updateConditionsInTx(Store store, AiLocalMatchConditionRequestDto request) {
        AiExposureStatus status = getOrCreateStatus(store);
        AiCustomerType customerType = request.getCustomerType() != null
                ? request.getCustomerType()
                : status.getCustomerType();
        int targetCount = estimateTargetCount(store.getStoreId(), customerType);
        status.updateConditions(request.getRadiusKm(), request.getInterest(), request.getCustomerType(), targetCount);
        return status;
    }

    private AiExposureStatus getOrCreateStatus(Store store) {
        return aiExposureStatusRepository.findByStore_StoreId(store.getStoreId())
                .orElseGet(() -> aiExposureStatusRepository.save(AiExposureStatus.init(store)));
    }

    private int estimateTargetCount(Long storeId, AiCustomerType customerType) {
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

        return switch (customerType) {
            case ALL -> allCustomers.size();
            case REGULAR -> countRegulars(storeId);
            case NEW -> Math.max(0, allCustomers.size() - orderAccountIds.size());
        };
    }

    private int countRegulars(Long storeId) {
        return (int) orderRepository.countRegularAccounts(storeId, OrderStatus.COMPLETED, 3);
    }
}
