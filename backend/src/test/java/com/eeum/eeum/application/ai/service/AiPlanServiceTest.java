package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiPlanResponseDto;
import com.eeum.eeum.application.ai.dto.response.PlanInfoDto;
import com.eeum.eeum.application.ai.policy.AiPlanPolicy;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanServiceTest {

    @InjectMocks
    private AiPlanService aiPlanService;

    @Mock private AiManagerSupportService supportService;
    @Spy private AiPlanPolicy aiPlanPolicy = new AiPlanPolicy();

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    private Store stubStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    @Test
    void FREE_플랜_사장은_현재_플랜과_월_사용량을_조회할_수_있다() {
        // given
        stubStore();
        when(supportService.getPlanType(STORE_ID)).thenReturn(AiPlanType.FREE);
        when(supportService.getMonthlyUsage(STORE_ID)).thenReturn(0L);

        // when
        AiPlanResponseDto result = aiPlanService.getPlans(OWNER_ID);

        // then
        assertThat(result.getCurrentPlan()).isEqualTo(AiPlanType.FREE);
        assertThat(result.getCurrentUsage()).isZero();
        assertThat(result.getPlans()).hasSize(AiPlanType.values().length);
    }

    @Test
    void BASIC_플랜은_월_한도가_50이다() {
        // given
        stubStore();
        when(supportService.getPlanType(STORE_ID)).thenReturn(AiPlanType.BASIC);
        when(supportService.getMonthlyUsage(STORE_ID)).thenReturn(5L);

        // when
        AiPlanResponseDto result = aiPlanService.getPlans(OWNER_ID);

        // then
        assertThat(result.getMonthlyLimit()).isEqualTo(50);
        assertThat(result.getCurrentUsage()).isEqualTo(5);
    }

    @Test
    void PRO_플랜은_월_한도가_200이다() {
        // given
        stubStore();
        when(supportService.getPlanType(STORE_ID)).thenReturn(AiPlanType.PRO);
        when(supportService.getMonthlyUsage(STORE_ID)).thenReturn(100L);

        // when
        AiPlanResponseDto result = aiPlanService.getPlans(OWNER_ID);

        // then
        assertThat(result.getMonthlyLimit()).isEqualTo(200);
    }

    @Test
    void 플랜_목록에_모든_플랜이_포함된다() {
        // given
        stubStore();
        when(supportService.getPlanType(STORE_ID)).thenReturn(AiPlanType.FREE);
        when(supportService.getMonthlyUsage(STORE_ID)).thenReturn(0L);

        // when
        AiPlanResponseDto result = aiPlanService.getPlans(OWNER_ID);

        // then
        assertThat(result.getPlans())
                .extracting(PlanInfoDto::getPlanType)
                .containsExactlyInAnyOrder(AiPlanType.values());
    }
}
