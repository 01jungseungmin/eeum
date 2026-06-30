package com.eeum.eeum.application.product.scheduler;

import com.eeum.eeum.application.product.service.EventProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventProductExpirationSchedulerTest {

    @InjectMocks
    private EventProductExpirationScheduler scheduler;

    @Mock
    private EventProductService eventProductService;

    // [시나리오 5] 스케줄러 자동 종료 — eventProductService.endExpiredEventProducts() 위임 확인
    @Test
    void 만료_이벤트_자동_종료_스케줄러_정상_실행() {
        // given
        when(eventProductService.endExpiredEventProducts()).thenReturn(3);

        // when
        scheduler.endExpiredEventProducts();

        // then
        verify(eventProductService).endExpiredEventProducts();
    }

    @Test
    void 만료된_이벤트가_없어도_정상_완료() {
        // given
        when(eventProductService.endExpiredEventProducts()).thenReturn(0);

        // when
        scheduler.endExpiredEventProducts();

        // then
        verify(eventProductService).endExpiredEventProducts();
    }
}
