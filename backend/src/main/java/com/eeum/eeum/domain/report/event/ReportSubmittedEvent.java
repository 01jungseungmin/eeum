package com.eeum.eeum.domain.report.event;

import com.eeum.eeum.domain.report.enums.ReportTargetType;

// 신고 접수 시 발행 — 관리자 전체에게 알림
public record ReportSubmittedEvent(
        Long reportId,
        Long reporterAccountId,
        String reporterNickname,
        ReportTargetType targetType,
        Long targetId
) {}
