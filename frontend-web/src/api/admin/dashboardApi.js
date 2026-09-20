import { apiClient } from '../apiClient';

// 관리자 대시보드 지표 API (AdminDashboardController)
export const dashboardApi = {
  // 상단 KPI 요약 (전체 회원 / 활성 사업장 / 오늘 거래 / 처리 대기)
  getSummary: () => {
    return apiClient.get('/admin/dashboard/summary');
  },

  // 처리 대기 항목 (사장 승인 / 신고 / 문의)
  getPendingActions: () => {
    return apiClient.get('/admin/dashboard/pending-actions');
  },

  // 일자별 가입자 추이 (type: GENERAL | OWNER, days: 1~90)
  getSignupTrend: (params) => {
    return apiClient.get('/admin/dashboard/signups', { params });
  },

  // 실시간 활동 (가입 / 가게 등록 / 결제 완료 / 신고 접수), limit: 1~50
  getActivities: (params) => {
    return apiClient.get('/admin/dashboard/activities', { params });
  },

  // 구·군별 활동 사용자 (limit: 1~50)
  getRegionMembers: (params) => {
    return apiClient.get('/admin/dashboard/regions', { params });
  },
};
