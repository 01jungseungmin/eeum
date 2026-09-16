package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportActionDispatcher {

    private final Map<ReportTargetType, ReportTargetActionExecutor> executors;

    public ReportActionDispatcher(List<ReportTargetActionExecutor> executors) {
        this.executors = new EnumMap<>(ReportTargetType.class);
        executors.forEach(executor -> this.executors.put(executor.targetType(), executor));
    }

    public Long execute(
            ReportTargetType targetType,
            ReportAction action,
            Long targetId,
            Long storedOwnerAccountId,
            String adminNote
    ) {
        ReportTargetActionExecutor executor = executors.get(targetType);
        if (executor == null) {
            throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        }
        return executor.execute(action, targetId, storedOwnerAccountId, adminNote);
    }
}
