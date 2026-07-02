package com.eeum.eeum.domain.report.repository;

import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = {"reporter"})
    Optional<Report> findByReportId(Long reportId);

    @EntityGraph(attributePaths = {"reporter"})
    Slice<Report> findByReporter_AccountId(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter"})
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter"})
    Page<Report> findAll(Pageable pageable);

    boolean existsByReporter_AccountIdAndTargetTypeAndTargetId(
            Long accountId,
            ReportTargetType targetType,
            Long targetId
    );
}
