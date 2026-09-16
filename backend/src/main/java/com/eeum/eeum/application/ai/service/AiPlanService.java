package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiPlanResponseDto;
import com.eeum.eeum.application.ai.dto.response.PlanInfoDto;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.application.ai.policy.AiPlanPolicy;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPlanService {

    private final AiManagerSupportService supportService;
    private final AiPlanPolicy aiPlanPolicy;

    @Transactional(readOnly = true)
    public AiPlanResponseDto getPlans(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.PLAN_VIEW);

        AiPlanType currentPlan = supportService.getPlanType(store.getStoreId());
        List<PlanInfoDto> plans = Arrays.stream(AiPlanType.values())
                .map(planType -> PlanInfoDto.builder()
                        .planType(planType)
                        .displayName(planType.getDisplayName())
                        .monthlyPrice(planType.getMonthlyPrice())
                        .features(aiPlanPolicy.featureDescriptions(planType))
                        .build())
                .toList();

        return AiPlanResponseDto.builder()
                .currentPlan(currentPlan)
                .plans(plans)
                .currentUsage(supportService.getMonthlyUsage(store.getStoreId()))
                .monthlyLimit(aiPlanPolicy.monthlyLimit(currentPlan))
                .build();
    }
}
