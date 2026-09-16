import { apiClient } from '../apiClient';

export const reportApi = {
  // 내 신고 목록 조회 / 상세 조회
  getReports: () => {
    return apiClient.get('/reports/me');
  },
  getReportDetail: (reportId) => {
    return apiClient.get(`/reports/${reportId}`);
  },

  // 신고 접수
  createReport: (reportData) => {
    return apiClient.post('/reports', reportData);
  },
};
