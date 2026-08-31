package com.eeum.eeum.domain.report.repository;

import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = {"reporter"})
    Optional<Report> findByReportId(Long reportId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.reportId = :reportId")
    Optional<Report> findByReportIdForUpdate(@Param("reportId") Long reportId);

    @EntityGraph(attributePaths = {"reporter"})
    Slice<Report> findByReporter_AccountId(Long accountId, Pageable pageable);

    // 내 신고 상세 — 소유자까지 조건에 넣어 조회한다.
    // 먼저 찾고 소유권을 따로 검사하면 남의 신고는 403, 없는 신고는 404가 되어
    // 임의의 reportId로 존재 여부를 알아낼 수 있다.
    Optional<Report> findByReportIdAndReporter_AccountId(Long reportId, Long accountId);

    @EntityGraph(attributePaths = {"reporter"})
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter"})
    Page<Report> findAll(Pageable pageable);

    boolean existsByReporter_AccountIdAndTargetTypeAndTargetId(
            Long accountId,
            ReportTargetType targetType,
            Long targetId
    );

    // 대시보드 요약 — 미처리(PENDING) 신고 건수
    long countByStatus(ReportStatus status);
}
