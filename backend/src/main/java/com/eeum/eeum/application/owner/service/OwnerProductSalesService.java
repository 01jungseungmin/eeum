package com.eeum.eeum.application.owner.service;

import com.eeum.eeum.application.owner.dto.response.OwnerCategorySalesResponseDto;
import com.eeum.eeum.application.owner.dto.response.OwnerProductSalesResponseDto;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.CategorySalesStat;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerProductSalesService {

    // 매출 지표이므로 거래가 끝난 주문만 센다. 취소·환불·만료 건은 판매로 잡히면 안 된다
    private static final List<OrderStatus> DEFAULT_STATUSES = List.of(OrderStatus.COMPLETED);

    private final StoreRepository storeRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public List<OwnerProductSalesResponseDto> getProductSales(
            Long ownerId,
            List<OrderStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    ) {
        validatePeriod(from, to);
        Long storeId = findStoreId(ownerId);

        return orderItemRepository
                .aggregateSoldQuantityByProduct(storeId, resolveStatuses(statuses), from, to)
                .stream()
                .map(OwnerProductSalesResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerCategorySalesResponseDto> getCategorySales(
            Long ownerId,
            List<OrderStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    ) {
        validatePeriod(from, to);
        Long storeId = findStoreId(ownerId);

        List<CategorySalesStat> stats = orderItemRepository
                .aggregateSalesByCategory(storeId, resolveStatuses(statuses), from, to);

        long totalQuantity = stats.stream().mapToLong(CategorySalesStat::soldQuantity).sum();
        BigDecimal totalAmount = stats.stream()
                .map(CategorySalesStat::salesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return stats.stream()
                .map(stat -> OwnerCategorySalesResponseDto.of(stat, totalQuantity, totalAmount))
                .toList();
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private Long findStoreId(Long ownerId) {
        return storeRepository.findByAccount_AccountId(ownerId)
                .map(Store::getStoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private List<OrderStatus> resolveStatuses(List<OrderStatus> statuses) {
        return (statuses == null || statuses.isEmpty()) ? DEFAULT_STATUSES : statuses;
    }
}
