package com.eeum.eeum.application.product.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.eeum.eeum.application.product.service.EventProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventProductExpirationScheduler {

    private final EventProductService eventProductService;

    // 1분 주기로 endAt이 지난 ACTIVE 이벤트 상품을 ENDED로 전환
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "endExpiredEventProducts", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void endExpiredEventProducts() {
        int count = eventProductService.endExpiredEventProducts();
        if (count > 0) {
            log.info("만료 이벤트 상품 종료 처리: {}건", count);
        }
    }
}
