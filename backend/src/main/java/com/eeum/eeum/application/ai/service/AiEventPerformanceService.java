package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiEventPerformanceResponseDto;
import com.eeum.eeum.application.ai.generator.AiInsightGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.enums.AiDiscountType;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiEventPerformanceService {

    private static final String DEFAULT_TIME_RANGE = "11:00~14:00";

    private final AiManagerSupportService supportService;
    private final AiInsightGenerator aiInsightGenerator;
    private final EventProductRepository eventProductRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public AiEventPerformanceResponseDto getPerformance(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.EVENT_PERFORMANCE_VIEW);
        Long storeId = store.getStoreId();

        List<EventProduct> events = eventProductRepository.findByProduct_Store_StoreIdOrderByCreatedAtDesc(storeId);
        if (events.isEmpty()) {
            return emptyResponse(store);
        }

        EventProduct latestEvent = events.get(0);
        List<Order> eventOrders = orderRepository.findByStore_StoreIdAndStatusAndCreatedAtBetween(
                storeId, OrderStatus.COMPLETED, latestEvent.getStartAt(), latestEvent.getEndAt());
        List<Order> allCompleted = orderRepository.findByStore_StoreIdAndStatus(storeId, OrderStatus.COMPLETED);

        Double newCustomerRatio = calculateNewCustomerRatio(eventOrders, allCompleted, latestEvent.getStartAt());
        long regularReorderCount = countRegularReorders(eventOrders, allCompleted);

        String summary = aiInsightGenerator.eventPerformanceSummary(
                store.getName(), eventOrders.size(), null, newCustomerRatio);

        return AiEventPerformanceResponseDto.builder()
                .productViewCount(null) // 상품 조회수 필드 미보유 — 2차에서 수집
                .eventOrderCount(eventOrders.size())
                .orderConversionRate(null) // 조회수가 없어 전환율 계산 불가
                .newCustomerRatio(newCustomerRatio)
                .regularReorderCount(regularReorderCount)
                .aiSummary(summary)
                .nextEventRecommendation(buildRecommendation(store, latestEvent))
                .hasData(!eventOrders.isEmpty())
                .emptyMessage(eventOrders.isEmpty() ? "이벤트 기간 주문 데이터가 아직 없습니다." : null)
                .build();
    }

    private AiEventPerformanceResponseDto emptyResponse(Store store) {
        return AiEventPerformanceResponseDto.builder()
                .productViewCount(null)
                .eventOrderCount(0)
                .orderConversionRate(null)
                .newCustomerRatio(null)
                .regularReorderCount(0)
                .aiSummary(aiInsightGenerator.eventPerformanceSummary(store.getName(), 0, null, null))
                .nextEventRecommendation(AiEventPerformanceResponseDto.NextEventRecommendationDto.builder()
                        .matchBasedExposure(false)
                        .reason(aiInsightGenerator.nextEventReason(store.getName(), null, DEFAULT_TIME_RANGE))
                        .build())
                .hasData(false)
                .emptyMessage("진행한 이벤트가 없습니다. 첫 이벤트를 등록해보세요.")
                .build();
    }

    // 이벤트 기간 첫 주문 고객 / 이벤트 기간 주문 고객 비율
    private Double calculateNewCustomerRatio(List<Order> eventOrders, List<Order> allCompleted, LocalDateTime eventStart) {
        if (eventOrders.isEmpty()) {
            return null;
        }
        Map<Long, LocalDateTime> firstOrderAtByAccount = allCompleted.stream()
                .collect(Collectors.toMap(
                        order -> order.getAccount().getAccountId(),
                        Order::getCreatedAt,
                        (a, b) -> a.isBefore(b) ? a : b));

        long eventCustomerCount = eventOrders.stream()
                .map(order -> order.getAccount().getAccountId()).distinct().count();
        long newCustomerCount = eventOrders.stream()
                .map(order -> order.getAccount().getAccountId()).distinct()
                .filter(accountId -> !firstOrderAtByAccount.get(accountId).isBefore(eventStart))
                .count();
        return (double) newCustomerCount / eventCustomerCount;
    }

    // 총 완료 주문 3건 이상 고객(단골)의 이벤트 기간 주문 수
    private long countRegularReorders(List<Order> eventOrders, List<Order> allCompleted) {
        Map<Long, Long> totalOrdersByAccount = allCompleted.stream()
                .collect(Collectors.groupingBy(order -> order.getAccount().getAccountId(), Collectors.counting()));
        return eventOrders.stream()
                .filter(order -> totalOrdersByAccount.getOrDefault(order.getAccount().getAccountId(), 0L) >= 3)
                .count();
    }

    // 직전 이벤트 상품/할인율 기반 프리필 데이터
    private AiEventPerformanceResponseDto.NextEventRecommendationDto buildRecommendation(Store store, EventProduct latestEvent) {
        BigDecimal originalPrice = latestEvent.getProduct().getPrice();
        Integer discountRate = null;
        if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
            discountRate = BigDecimal.ONE
                    .subtract(latestEvent.getEventPrice().divide(originalPrice, 4, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }
        return AiEventPerformanceResponseDto.NextEventRecommendationDto.builder()
                .recommendedProductId(latestEvent.getProduct().getProductId())
                .recommendedProductName(latestEvent.getProduct().getName())
                .discountType(AiDiscountType.PERCENT)
                .discountRate(discountRate)
                .discountAmount(null)
                .recommendedTimeRange(DEFAULT_TIME_RANGE)
                .matchBasedExposure(latestEvent.getSoldCount() > 0)
                .reason(aiInsightGenerator.nextEventReason(
                        store.getName(), latestEvent.getProduct().getName(), DEFAULT_TIME_RANGE))
                .build();
    }
}
