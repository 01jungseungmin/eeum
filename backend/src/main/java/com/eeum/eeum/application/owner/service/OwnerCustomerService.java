package com.eeum.eeum.application.owner.service;

import com.eeum.eeum.application.order.dto.response.OrderItemResponseDto;
import com.eeum.eeum.application.owner.dto.response.OwnerCustomerResponseDto;
import com.eeum.eeum.application.owner.dto.response.OwnerCustomerSummaryResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreOrderResponseDto;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.application.owner.enums.OwnerCustomerInterestType;
import com.eeum.eeum.application.owner.enums.OwnerCustomerSortType;
import com.eeum.eeum.application.owner.enums.OwnerCustomerType;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.CustomerOrderStatProjection;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.CustomerReviewStatProjection;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerCustomerService {

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final FavoriteRepository favoriteRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Page<OwnerCustomerResponseDto> getCustomers(
            Long ownerId,
            OwnerCustomerType customerType,
            OwnerCustomerInterestType interestType,
            OwnerCustomerSortType sort,
            Pageable pageable
    ) {
        Long storeId = getOwnerStoreId(ownerId);
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);

        // 합집합 구성 (3 queries)
        Set<Long> orderAccountIds = new HashSet<>(orderRepository.findOrderAccountIdsByStoreIdAndStatus(storeId,OrderStatus.COMPLETED));
        Set<Long> favoriteAccountIds = new HashSet<>(favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, storeId));
        Set<Long> chatAccountIds = new HashSet<>(chatParticipantRepository.findActiveParticipantAccountIdsByStoreRefId(
                storeId, ChatRoomRefType.STORE, ParticipantStatus.ACTIVE));

        orderAccountIds.remove(ownerId);
        favoriteAccountIds.remove(ownerId);
        chatAccountIds.remove(ownerId);

        Set<Long> allAccountIds = new HashSet<>();
        allAccountIds.addAll(orderAccountIds);
        allAccountIds.addAll(favoriteAccountIds);
        allAccountIds.addAll(chatAccountIds);

        if (allAccountIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> accountIdList = new ArrayList<>(allAccountIds);

        // 배치 조회 (3 queries)
        Map<Long, Account> accountMap = accountRepository.findAllById(accountIdList)
                .stream().collect(Collectors.toMap(Account::getAccountId, Function.identity()));

        Map<Long, CustomerOrderStatProjection> orderStatMap = orderRepository
                .findOrderStatsByStoreAndAccounts(storeId, accountIdList, OrderStatus.COMPLETED, sixMonthsAgo)
                .stream().collect(Collectors.toMap(CustomerOrderStatProjection::getAccountId, Function.identity()));

        Map<Long, CustomerReviewStatProjection> reviewStatMap = storeReviewRepository
                .findReviewStatsByStoreAndAccounts(storeId, accountIdList)
                .stream().collect(Collectors.toMap(CustomerReviewStatProjection::getAccountId, Function.identity()));

        // 고객 데이터 빌드 + 필터 적용
        List<OwnerCustomerResponseDto> filtered = accountIdList.stream()
                .filter(accountMap::containsKey)
                .map(accountId -> {
                    Account account = accountMap.get(accountId);
                    CustomerOrderStatProjection orderStat = orderStatMap.get(accountId);
                    CustomerReviewStatProjection reviewStat = reviewStatMap.get(accountId);

                    boolean isFavorite = favoriteAccountIds.contains(accountId);
                    boolean isChatParticipant = chatAccountIds.contains(accountId);
                    long orderCount = orderStat != null ? orderStat.getOrderCount() : 0L;
                    long recentOrderCount = orderStat != null ? orderStat.getRecentOrderCount() : 0L;

                    OwnerCustomerType resolvedType = resolveCustomerType(orderCount, recentOrderCount);

                    return OwnerCustomerResponseDto.builder()
                            .customerId(accountId)
                            .maskedName(maskName(account.getName()))
                            .maskedPhone(maskPhone(account.getPhone()))
                            .customerType(resolvedType)
                            .favorite(isFavorite)
                            .chatParticipant(isChatParticipant)
                            .totalOrderCount((int) orderCount)
                            .totalOrderAmount(orderStat != null ? orderStat.getTotalAmount() : BigDecimal.ZERO)
                            .reviewCount(reviewStat != null ? reviewStat.getReviewCount().intValue() : 0)
                            .averageRating(reviewStat != null ? reviewStat.getAvgRating() : null)
                            .lastOrderDate(orderStat != null && orderStat.getLastOrderedAt() != null
                                    ? orderStat.getLastOrderedAt().toLocalDate() : null)
                            .build();
                })
                .filter(dto -> matchesCustomerType(dto, customerType))
                .filter(dto -> matchesInterestType(dto, interestType))
                .sorted(comparatorFor(sort))
                .collect(Collectors.toList());

        // 수동 페이지네이션
        int total = filtered.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<OwnerCustomerResponseDto> pageContent = start >= total
                ? Collections.emptyList()
                : filtered.subList(start, end);

        return new PageImpl<>(pageContent, pageable, total);
    }

    @Transactional(readOnly = true)
    public OwnerCustomerSummaryResponseDto getCustomerSummary(Long ownerId) {
        Long storeId = getOwnerStoreId(ownerId);
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);

        Set<Long> orderAccountIds = new HashSet<>(orderRepository.findOrderAccountIdsByStoreIdAndStatus(storeId,OrderStatus.COMPLETED));
        Set<Long> favoriteAccountIds = new HashSet<>(favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, storeId));
        Set<Long> chatAccountIds = new HashSet<>(chatParticipantRepository.findActiveParticipantAccountIdsByStoreRefId(
                storeId, ChatRoomRefType.STORE, ParticipantStatus.ACTIVE));

        orderAccountIds.remove(ownerId);
        favoriteAccountIds.remove(ownerId);
        chatAccountIds.remove(ownerId);

        Set<Long> allAccountIds = new HashSet<>();
        allAccountIds.addAll(orderAccountIds);
        allAccountIds.addAll(favoriteAccountIds);
        allAccountIds.addAll(chatAccountIds);
        BigDecimal totalSales = orderRepository.sumOrderAmountByStoreIdAndStatus(storeId,OrderStatus.COMPLETED);

        if (allAccountIds.isEmpty()) {
            return OwnerCustomerSummaryResponseDto.builder()
                    .totalCustomerCount(0)
                    .regularCustomerCount(0)
                    .newCustomerCount(0)
                    .potentialCustomerCount(0)
                    .favoriteCustomerCount(0)
                    .chatParticipantCustomerCount(0)
                    .totalSalesAmount(totalSales != null ? totalSales : BigDecimal.ZERO)
                    .build();
        }

        List<Long> accountIdList = new ArrayList<>(allAccountIds);

        Set<Long> existingAccountIds = accountRepository.findAllById(accountIdList).stream()
                .map(Account::getAccountId)
                .collect(Collectors.toSet());

        allAccountIds.retainAll(existingAccountIds);
        favoriteAccountIds.retainAll(existingAccountIds);
        chatAccountIds.retainAll(existingAccountIds);

        if (allAccountIds.isEmpty()) {
            return OwnerCustomerSummaryResponseDto.builder()
                    .totalCustomerCount(0)
                    .regularCustomerCount(0)
                    .normalCustomerCount(0)
                    .newCustomerCount(0)
                    .potentialCustomerCount(0)
                    .favoriteCustomerCount(0)
                    .chatParticipantCustomerCount(0)
                    .totalSalesAmount(totalSales != null ? totalSales : BigDecimal.ZERO)
                    .build();
        }

        accountIdList = new ArrayList<>(allAccountIds);

        Map<Long, CustomerOrderStatProjection> orderStatMap = orderRepository
                .findOrderStatsByStoreAndAccounts(
                        storeId,
                        accountIdList,
                        OrderStatus.COMPLETED,
                        sixMonthsAgo
                )
                .stream()
                .collect(Collectors.toMap(
                        CustomerOrderStatProjection::getAccountId,
                        Function.identity()
                ));

        long regularCount = 0;
        long normalCount = 0;
        long newCount = 0;
        long potentialCount = 0;

        for (Long accountId : allAccountIds) {
            CustomerOrderStatProjection stat = orderStatMap.get(accountId);
            long orderCount = stat != null ? stat.getOrderCount() : 0L;
            long recentCount = stat != null ? stat.getRecentOrderCount() : 0L;
            OwnerCustomerType type = resolveCustomerType(orderCount, recentCount);
            if (type == OwnerCustomerType.REGULAR) regularCount++;
            else if(type == OwnerCustomerType.NORMAL) normalCount++;
            else if (type == OwnerCustomerType.NEW) newCount++;
            else if (type == OwnerCustomerType.POTENTIAL) potentialCount++;
        }

        return OwnerCustomerSummaryResponseDto.builder()
                .totalCustomerCount(allAccountIds.size())
                .regularCustomerCount(regularCount)
                .normalCustomerCount(normalCount)
                .newCustomerCount(newCount)
                .potentialCustomerCount(potentialCount)
                .favoriteCustomerCount(favoriteAccountIds.size())
                .chatParticipantCustomerCount(chatAccountIds.size())
                .totalSalesAmount(totalSales != null ? totalSales : BigDecimal.ZERO)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<StoreOrderResponseDto> getCustomerOrders(Long ownerId, Long customerId, Pageable pageable) {
        Long storeId = getOwnerStoreId(ownerId);
        Page<Order> orders = orderRepository
                .findByStore_StoreIdAndAccount_AccountIdOrderByCreatedAtDesc(storeId, customerId, pageable);
        return orders.map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrder_OrderId(order.getOrderId());
            Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId()).orElse(null);
            return StoreOrderResponseDto.of(order, items, payment);
        });
    }

    // ===================== 내부 유틸 =====================

    private Long getOwnerStoreId(Long ownerId) {
        return storeRepository.findByAccount_AccountId(ownerId)
                .map(Store::getStoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private OwnerCustomerType resolveCustomerType(long orderCount, long recentOrderCount) {
        if (orderCount == 0) return OwnerCustomerType.POTENTIAL;
        if (recentOrderCount >= 3) return OwnerCustomerType.REGULAR;
        if (orderCount >= 2) return OwnerCustomerType.NORMAL;
        return OwnerCustomerType.NEW;
    }

    private boolean matchesCustomerType(OwnerCustomerResponseDto dto, OwnerCustomerType filter) {
        return filter == OwnerCustomerType.ALL || dto.getCustomerType() == filter;
    }

    private boolean matchesInterestType(OwnerCustomerResponseDto dto, OwnerCustomerInterestType filter) {
        return switch (filter) {
            case ALL -> true;
            case FAVORITE -> dto.isFavorite();
            case CHAT_PARTICIPANT -> dto.isChatParticipant();
        };
    }

    private Comparator<OwnerCustomerResponseDto> comparatorFor(OwnerCustomerSortType sort) {
        Comparator<OwnerCustomerResponseDto> comparator = switch (sort) {
            case RECENT_ORDER_DESC -> Comparator.comparing(
                    OwnerCustomerResponseDto::getLastOrderDate,
                    Comparator.nullsLast(Comparator.reverseOrder()));

            case ORDER_COUNT_DESC -> Comparator.comparingInt(
                    OwnerCustomerResponseDto::getTotalOrderCount).reversed();

            case TOTAL_AMOUNT_DESC -> Comparator.comparing(
                    OwnerCustomerResponseDto::getTotalOrderAmount,
                    Comparator.nullsLast(Comparator.reverseOrder()));

            case REVIEW_RATING_DESC -> Comparator.comparing(
                    OwnerCustomerResponseDto::getAverageRating,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };

        return comparator.thenComparing(OwnerCustomerResponseDto::getCustomerId);
    }

    private static String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    private static String maskPhone(String phone) {
        if (phone == null) return null;
        // 010-1234-5678 → 010-****-5678 / 01012345678 → 010****5678
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return "***";
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        String masked = head + "****" + tail;
        // 원본에 하이픈이 있으면 포맷 복원
        if (phone.contains("-")) {
            return head + "-****-" + tail;
        }
        return masked;
    }
}
