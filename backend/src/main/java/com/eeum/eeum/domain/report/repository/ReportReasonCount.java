package com.eeum.eeum.domain.report.repository;

import com.eeum.eeum.domain.report.enums.ReportReason;

/** 신고 사유별 건수 집계 원값. */
public record ReportReasonCount(ReportReason reason, Long count) {
}
