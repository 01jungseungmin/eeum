import { apiClient } from '../apiClient';

export const aiManagerApi = {
  // AI 매니저 메인 대시보드 조회 (고객 케어 / 리뷰·문의 / 이벤트 성과 / 생활권 / 운영 위험 / 활동 요약 통합)
  getDashboard: () => {
    return apiClient.get('/owner/ai-manager/dashboard');
  },

  // AI 활동 요약 조회
  getActivitySummary: () => {
    return apiClient.get('/owner/ai-manager/activities/summary');
  },

  // 플랜 관리 조회 (현재 플랜, 플랜 목록, 이번 달 사용량)
  getPlans: () => {
    return apiClient.get('/owner/ai-manager/plans');
  },
};
