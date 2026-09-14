package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.application.settlement.dto.response.OwnerRevenueResponseDto;
import com.eeum.eeum.application.settlement.dto.response.WeeklySettlementResponseDto;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final StoreRepository storeRepository;
    private final OwnerRevenueRepository ownerRevenueRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;

    @Transactional(readOnly = true)
    public Page<OwnerRevenueResponseDto> getOwnerRevenues(Long accountId, Pageable pageable) {
        Long storeId = resolveStoreId(accountId);
        return ownerRevenueRepository.findByStore_StoreIdOrderByCreatedAtDesc(storeId, pageable)
                .map(OwnerRevenueResponseDto::from);
    }

    @Transactional(readOnly = true)
    public Page<WeeklySettlementResponseDto> getOwnerWeeklySettlements(Long accountId, Pageable pageable) {
        Long storeId = resolveStoreId(accountId);
        return weeklySettlementRepository.findByStore_StoreIdOrderByPeriodEndAtDesc(storeId, pageable)
                .map(WeeklySettlementResponseDto::from);
    }

    @Transactional(readOnly = true)
    public Page<WeeklySettlementResponseDto> getAdminWeeklySettlements(Pageable pageable) {
        return weeklySettlementRepository.findAllByOrderByPeriodEndAtDesc(pageable)
                .map(WeeklySettlementResponseDto::from);
    }

    private Long resolveStoreId(Long accountId) {
        return storeRepository.findStoreIdByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
