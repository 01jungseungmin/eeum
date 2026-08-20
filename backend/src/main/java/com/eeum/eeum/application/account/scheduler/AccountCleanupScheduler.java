package com.eeum.eeum.application.account.scheduler;

import com.eeum.eeum.application.account.service.AccountCleanupService;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCleanupScheduler {

    private final AccountCleanupService accountCleanupService;
    private final OperationFailureRecorder operationFailureRecorder;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupWithdrawnAccounts() {
        List<Long> targetIds = accountCleanupService.findDeletableAccountIds();

        if (targetIds.isEmpty()) {
            log.info("영구 삭제 대상 탈퇴 계정 없음");
            return;
        }

        log.info("탈퇴 계정 영구 삭제 스케줄러 시작: 대상={}건", targetIds.size());

        int deleted = 0;
        int failed = 0;

        // 계정별로 처리한다. 한 건이 실패해도 나머지는 삭제돼야 한다 —
        // 하나로 묶으면 남은 참조가 있는 계정 하나 때문에 그날 회차 전체가 롤백된다.
        for (Long accountId : targetIds) {
            try {
                accountCleanupService.deleteAccount(accountId);
                deleted++;
            } catch (Exception e) {
                failed++;
                // 남은 FK 참조 때문에 실패하는 계정은 반복해서 실패한다.
                // 로그만 남기면 아무도 모르므로 운영 실패 이력으로 올려 관리자가 볼 수 있게 한다.
                log.warn("탈퇴 계정 영구 삭제 실패: accountId={}", accountId, e);
                operationFailureRecorder.record(
                        OperationFailureCategory.SCHEDULER,
                        "ACCOUNT_CLEANUP",
                        "ACCOUNT",
                        String.valueOf(accountId),
                        e,
                        null);
            }
        }

        log.info("탈퇴 계정 영구 삭제 스케줄러 종료: 성공={}건, 실패={}건", deleted, failed);
    }
}
