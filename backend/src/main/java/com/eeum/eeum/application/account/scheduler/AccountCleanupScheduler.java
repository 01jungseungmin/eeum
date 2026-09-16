package com.eeum.eeum.application.account.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

    // 한 번에 읽을 대상 수. 크게 잡으면 조회만으로 잠금 보유 시간을 밀어낸다.
    private static final int BATCH_SIZE = 100;

    // 한 회차에서 처리할 상한. backlog가 쌓여 있어도 이 선에서 멈추고 다음 회차로 넘긴다 —
    // ShedLock 보유 시간(PT30M)을 넘기면 다른 인스턴스가 같은 작업을 시작할 수 있다.
    private static final int MAX_PER_RUN = 5_000;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "cleanupWithdrawnAccounts", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void cleanupWithdrawnAccounts() {
        int anonymized = 0;
        int failed = 0;
        long lastAccountId = 0L;

        // 대상을 한 번에 다 읽지 않고 ID 커서로 나눠 읽는다.
        // 실패한 계정은 anonymizedAt이 비어 있어 다음 회차에 다시 대상이 되지만,
        // 커서가 앞으로만 가므로 같은 회차에서 무한히 반복하지는 않는다.
        while (anonymized + failed < MAX_PER_RUN) {
            List<Long> targetIds =
                    accountCleanupService.findAnonymizeTargetIds(lastAccountId, BATCH_SIZE);

            if (targetIds.isEmpty()) {
                break;
            }

            for (Long accountId : targetIds) {
                lastAccountId = accountId;
                try {
                    accountCleanupService.anonymizeAccount(accountId);
                    anonymized++;
                } catch (Exception e) {
                    failed++;
                    // 남은 참조나 제약 때문에 반복해서 실패하는 계정은 로그만 남기면 아무도 모른다.
                    // 운영 실패 이력으로 올려 관리자가 볼 수 있게 한다.
                    log.warn("탈퇴 계정 개인정보 파기 실패: accountId={}", accountId, e);
                    operationFailureRecorder.record(
                            OperationFailureCategory.SCHEDULER,
                            "ACCOUNT_CLEANUP",
                            "ACCOUNT",
                            String.valueOf(accountId),
                            e,
                            null);
                }
            }
        }

        if (anonymized == 0 && failed == 0) {
            log.info("개인정보 파기 대상 탈퇴 계정 없음");
            return;
        }

        log.info("탈퇴 계정 개인정보 파기 스케줄러 종료: 성공={}건, 실패={}건, 마지막 accountId={}",
                anonymized, failed, lastAccountId);
    }
}
