package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiEventPerformanceResponseDto;
import com.eeum.eeum.application.ai.generator.AiInsightGenerator;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEventPerformanceServiceTest {

    @InjectMocks
    private AiEventPerformanceService aiEventPerformanceService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiInsightGenerator aiInsightGenerator;
    @Mock private EventProductRepository eventProductRepository;
    @Mock private OrderRepository orderRepository;

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    private Store stubStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getName()).thenReturn("테스트 가게");
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    @Test
    void 이벤트가_없으면_빈_응답을_반환한다() {
        // given
        stubStore();
        when(eventProductRepository.findFirstByProduct_Store_StoreIdOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.empty());
        when(aiInsightGenerator.eventPerformanceSummary(any(), anyLong(), any(), any()))
                .thenReturn("이벤트를 등록해보세요.");
        when(aiInsightGenerator.nextEventReason(any(), any(), any()))
                .thenReturn("첫 이벤트를 시작하세요.");

        // when
        AiEventPerformanceResponseDto result = aiEventPerformanceService.getPerformance(OWNER_ID);

        // then
        assertThat(result.isHasData()).isFalse();
        assertThat(result.getEmptyMessage()).contains("이벤트");
        assertThat(result.getEventOrderCount()).isZero();
        assertThat(result.getProductViewCount()).isNull();
    }
}
