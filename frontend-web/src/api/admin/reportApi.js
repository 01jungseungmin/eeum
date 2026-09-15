import { apiClient } from '../apiClient';

export const reportApi = {
  // 신고 목록 조회
  getReportList: () => {
    return apiClient.get('/admin/reports');
  },
  // 신고 상세 조회
  getReportDetail: (reportId) => {
    return apiClient.get(`/admin/reports/${reportId}`);
  },
  // 신고 처리 저장
  processReport: (reportId, data) => {
    return apiClient.put(`/admin/reports/${reportId}`, data);
  },

  // 신고 기각 API
  dismissReport: (reportId, adminNote) => {
    return apiClient.patch(`/admin/reports/${reportId}/dismiss`, {
      adminNote,
    });
  },
  // 신고 검토 완료
  reviewReport: (reportId, adminNote) => {
    return apiClient.patch(`/admin/reports/${reportId}/review`, {
      adminNote,
    });
  },
};
