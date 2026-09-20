package com.eeum.eeum.domain.report.repository;

import com.eeum.eeum.domain.report.enums.ReportStatus;

import java.util.List;

public interface ReportRepositoryCustom {

    // 관리자 대시보드 처리 대기 — 상태별 신고의 사유별 건수. 건수가 0인 사유는 행이 없다
    List<ReportReasonCount> countByReasonForStatus(ReportStatus status);
}
