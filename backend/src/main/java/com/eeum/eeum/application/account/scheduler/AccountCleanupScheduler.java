package com.eeum.eeum.application.account.scheduler;

import com.eeum.eeum.application.account.service.AccountCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCleanupScheduler {

    private final AccountCleanupService accountCleanupService;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupWithdrawnAccounts() {
        log.info("탈퇴 계정 영구 삭제 스케줄러 시작");
        accountCleanupService.deleteWithdrawnAccountsAfter30Days();
        log.info("탈퇴 계정 영구 삭제 스케줄러 종료");
    }
}