// AI 매니저 상세 페이지별 최소 요구 플랜.
// 백엔드 AiPlanPolicy/AiFeature 정책의 축소판 — 값이 어긋나도 기존의
// "API 호출 후 실패 시 업그레이드 모달" 안전망이 그대로 남아있으므로
// 여기서는 확실한 경우에만 API 호출 자체를 막는 용도로 사용한다.
export const AI_PLAN_RANK = {
  FREE: 0,
  BASIC: 1,
  PRO: 2,
};

export const AI_PAGE_REQUIRED_PLAN = {
  savingPlan: 'PRO', // AiSavingPlanCreatePage — SAVING_PLAN
  operationRiskDetail: 'PRO', // AiOperationRiskDetailPage — OPERATION_RISK_DETAIL
  chat: 'BASIC', // AiChatPage — CHATBOT (메시지 전송 시점에 확인)
  powerUsageReport: 'PRO', // AiPowerUsageReportPage — ELECTRICITY_REPORT
  locationMatchExposure: 'PRO', // AiLocationMatchingDetailPage 노출 시작 — LOCAL_MATCH_EXPOSURE
};

// 캐싱된 플랜(aiPlanType)이 필요한 플랜을 충족하는지 확인.
// aiPlanType이 아직 없으면(조회 실패/미확정) 막지 않고 통과시킨다 — 안전망 유지.
export const hasRequiredPlan = (aiPlanType, requiredPlan) => {
  if (!aiPlanType) return true;
  return AI_PLAN_RANK[aiPlanType] >= AI_PLAN_RANK[requiredPlan];
};
