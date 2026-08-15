package com.eeum.eeum.application.sanction.service;

import com.eeum.eeum.application.sanction.dto.response.SanctionHistoryResponseDto;
import com.eeum.eeum.domain.sanction.entity.SanctionHistory;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.sanction.enums.SanctionSource;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import com.eeum.eeum.domain.sanction.repository.SanctionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SanctionHistoryService {

    private final SanctionHistoryRepository sanctionHistoryRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDirectAccountAction(
            Long accountId,
            SanctionAction action,
            Long adminId
    ) {
        record(
                SanctionTargetType.ACCOUNT,
                accountId,
                action,
                SanctionSource.DIRECT_ADMIN,
                null,
                adminId,
                null
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDirectStoreAction(
            Long storeId,
            SanctionAction action,
            Long adminId
    ) {
        record(
                SanctionTargetType.STORE,
                storeId,
                action,
                SanctionSource.DIRECT_ADMIN,
                null,
                adminId,
                null
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReportAction(
            SanctionTargetType targetType,
            Long targetId,
            SanctionAction action,
            String adminNote,
            Long adminId,
            Long reportId
    ) {
        record(
                targetType,
                targetId,
                action,
                SanctionSource.REPORT,
                adminNote,
                adminId,
                reportId
        );
    }

    @Transactional(readOnly = true)
    public Page<SanctionHistoryResponseDto> getAccountHistories(
            Long accountId,
            Pageable pageable
    ) {
        return getHistories(SanctionTargetType.ACCOUNT, accountId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SanctionHistoryResponseDto> getStoreHistories(
            Long storeId,
            Pageable pageable
    ) {
        return getHistories(SanctionTargetType.STORE, storeId, pageable);
    }

    private void record(
            SanctionTargetType targetType,
            Long targetId,
            SanctionAction action,
            SanctionSource source,
            String adminNote,
            Long adminId,
            Long reportId
    ) {
        sanctionHistoryRepository.save(SanctionHistory.record(
                targetType,
                targetId,
                action,
                source,
                adminNote,
                adminId,
                reportId
        ));
    }

    private Page<SanctionHistoryResponseDto> getHistories(
            SanctionTargetType targetType,
            Long targetId,
            Pageable pageable
    ) {
        return sanctionHistoryRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDescSanctionHistoryIdDesc(
                        targetType,
                        targetId,
                        pageable
                )
                .map(SanctionHistoryResponseDto::from);
    }
}
