package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;

public interface ReportTargetActionExecutor {

    ReportTargetType targetType();

    Long execute(
            ReportAction action,
            Long targetId,
            Long storedOwnerAccountId,
            String adminNote
    );
}
