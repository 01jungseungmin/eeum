package com.eeum.eeum.application.ai.policy;

import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPlanPolicyTest {

    private final AiPlanPolicy policy = new AiPlanPolicy();

    @Test
    void 플랜이_기능_요구_플랜보다_낮으면_AI_PLAN_REQUIRED_예외가_발생한다() {
        // given — CHATBOT은 BASIC 이상
        // when & then
        assertThatThrownBy(() -> policy.validateAccess(AiPlanType.FREE, AiFeature.CHATBOT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_REQUIRED);
    }

    @Test
    void BASIC_플랜은_PRO_전용_기능에_접근하면_AI_PLAN_REQUIRED_예외가_발생한다() {
        // given — SAVING_PLAN은 PRO 전용
        // when & then
        assertThatThrownBy(() -> policy.validateAccess(AiPlanType.BASIC, AiFeature.SAVING_PLAN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_REQUIRED);
    }

    @Test
    void 플랜이_요구_플랜_이상이면_접근이_허용된다() {
        // given & when & then
        assertThatCode(() -> policy.validateAccess(AiPlanType.FREE, AiFeature.DASHBOARD))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateAccess(AiPlanType.BASIC, AiFeature.CHATBOT))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateAccess(AiPlanType.PRO, AiFeature.SAVING_PLAN))
                .doesNotThrowAnyException();
    }

    @Test
    void 생성성_기능만_사용량_카운트_대상이다() {
        // given & when & then
        assertThat(policy.isUsageCounted(AiFeature.MARKETING_DRAFT)).isTrue();
        assertThat(policy.isUsageCounted(AiFeature.CHATBOT_GENERATION)).isTrue();
        assertThat(policy.isUsageCounted(AiFeature.DASHBOARD)).isFalse();
        assertThat(policy.isUsageCounted(AiFeature.CHATBOT)).isFalse();
    }

    @Test
    void 월_사용_한도는_FREE_5회_BASIC_50회_PRO_200회이다() {
        // given & when & then
        assertThat(policy.monthlyLimit(AiPlanType.FREE)).isEqualTo(5);
        assertThat(policy.monthlyLimit(AiPlanType.BASIC)).isEqualTo(50);
        assertThat(policy.monthlyLimit(AiPlanType.PRO)).isEqualTo(200);
    }

    @Test
    void 플랜별_기능_설명_목록이_비어있지_않게_반환된다() {
        // given & when & then
        assertThat(policy.featureDescriptions(AiPlanType.FREE)).isNotEmpty();
        assertThat(policy.featureDescriptions(AiPlanType.BASIC))
                .anyMatch(description -> description.contains("월 50회"));
        assertThat(policy.featureDescriptions(AiPlanType.PRO)).isNotEmpty();
    }
}
