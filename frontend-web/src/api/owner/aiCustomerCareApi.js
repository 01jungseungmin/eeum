import { apiClient } from '../apiClient';

export const aiCustomerCareApi = {
  // AI 고객 케어 카드 전체 조회 (구매 관심 / 방문 없는 단골 / 문의 후 망설임)
  getCareCards: () => {
    return apiClient.get('/owner/ai-manager/customer-care');
  },

  // AI 고객 케어 카드 단건 조회
  getCareCard: (careType) => {
    return apiClient.get(`/owner/ai-manager/customer-care/${careType}`);
  },

  // 케어 유형별 메시지 초안 생성 (Basic 이상, 월 사용량 카운트)
  createDraft: (careType, draftData) => {
    return apiClient.post(
      `/owner/ai-manager/customer-care/${careType}/draft`,
      draftData,
    );
  },
};
