package com.eeum.eeum.application.external.service;

import com.eeum.eeum.domain.external.entity.ExternalDataImportHistory;
import com.eeum.eeum.domain.external.enums.ExternalImportStatus;
import com.eeum.eeum.domain.external.repository.ExternalDataImportHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Import 실패 이력 기록 전용 — REQUIRES_NEW로 즉시 커밋한다.
 * ExternalDataImportService.validateAnySuccess가 이력을 남긴 직후 BusinessException을 던지는데,
 * 같은 트랜잭션에서 처리하면 예외로 인한 롤백에 이력 저장까지 함께 사라져 감사 로그가 남지 않는 문제가 있었다.
 */
@Service
@RequiredArgsConstructor
public class ExternalImportFailureRecorder {

    private final ExternalDataImportHistoryRepository importHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String dataName, String sourceId, MultipartFile file, LocalDate sourceUpdatedAt,
            int total, int failed, String failureReason
    ) {
        importHistoryRepository.save(ExternalDataImportHistory.record(
                dataName, sourceId, file.getOriginalFilename(), sourceUpdatedAt,
                ExternalImportStatus.FAILED, total, 0, failed, failureReason));
    }
}
