import { apiClient } from '../apiClient';

export const aiManagerApi = {
  // 0. AI 매니저 메인 대시보드 조회, 활동 요약 조회
  getDashboard: () => {
    return apiClient.get('/owner/ai-manager/dashboard');
  },
  getActivitySummary: () => {
    return apiClient.get('/owner/ai-manager/activities/summary');
  },

  // 1. 챗봇
  // 고정 추천 질문 조회, 챗봇 메시지 전송
  getQuickQuestions: () => {
    return apiClient.get('/owner/ai-manager/chat/quick-questions');
  },
  sendChatMessage: (payload) => {
    // payload: { quickQuestionId?: number, text?: string }
    return apiClient.post('/owner/ai-manager/chat/messages', payload);
  },

  // 2. AI 고객 케어
  // 고객 케어 카드 전체, 단건 조회, 메시지 초안 생성
  getCustomerCareCards: () => {
    return apiClient.get('/owner/ai-manager/customer-care');
  },
  getCustomerCareCardByType: (careType) => {
    return apiClient.get(`/owner/ai-manager/customer-care/${careType}`);
  },
  createCustomerCareCard: (careType) => {
    return apiClient.post(`/owner/ai-manager/customer-care/${careType}/draft`);
  },

  // 3. AI 생성 메시지
  // AI 생성 메시지 목록 조회, 상세 조회, 수정, 취소, 예약 발송, 검토 후 보내기
  getGeneratedMessages: () => {
    return apiClient.get('/owner/ai-manager/generated-messages');
  },
  getGeneratedMessageById: (messageId) => {
    return apiClient.get(`/owner/ai-manager/generated-messages/${messageId}`);
  },
  updateGeneratedMessage: (messageId, payload) => {
    return apiClient.patch(
      `/owner/ai-manager/generated-messages/${messageId}`,
      payload,
    );
  },
  cancelGeneratedMessage: (messageId) => {
    return apiClient.post(
      `/owner/ai-manager/generated-messages/${messageId}/cancel`,
    );
  },
  scheduleGeneratedMessage: (messageId, payload) => {
    return apiClient.post(
      `/owner/ai-manager/generated-messages/${messageId}/schedule`,
      payload,
    );
  },
  sendGeneratedMessage: (messageId) => {
    return apiClient.post(
      `/owner/ai-manager/generated-messages/${messageId}/send`,
    );
  },

  // 문의 답변 초안 생성
  createInquiryReplyDraft: (inquiryId, confirmDelete = false) => {
    return apiClient.post(
      `/owner/ai-manager/inquiries/${inquiryId}/reply-draft`,
      {
        params: { confirmDelete },
      },
    );
  },

  // 4. 생활권 매칭
  // 분석 조회, 조건 변경, 노출 상태 조회, 노출 시작, 노출 중지
  getLocalMatchAnalysis: () => {
    return apiClient.get('/owner/ai-manager/local-match');
  },
  updateLocalMatchConditions: (payload) => {
    return apiClient.patch('/owner/ai-manager/local-match/conditions', payload);
  },
  getExposureStatus: () => {
    return apiClient.get('/owner/ai-manager/local-match/exposure');
  },
  startExposure: () => {
    return apiClient.post('/owner/ai-manager/local-match/exposure');
  },
  stopExposure: () => {
    return apiClient.post('/owner/ai-manager/local-match/exposure/stop');
  },

  // 5. 마케팅 자동화
  // 개요 조회, 문구 초안 생성
  getMarketingOverview: () => {
    return apiClient.get('/owner/ai-manager/marketing');
  },
  createMarketingDraft: (data) => {
    return apiClient.post(`/owner/ai-manager/marketing/draft`, data);
  },

  // 6. 공지사항
  // 공지 등록, 공지 예약 등록, 공지 등록용 초안 생성
  createNotice: (messageId) => {
    return apiClient.post(`/owner/ai-manager/notices/${messageId}/public`);
  },
  scheduleNotice: (messageId, data) => {
    return apiClient.post(
      `/owner/ai-manager/notices/${messageId}/schedule`,
      data,
    );
  },
  createNoticeDraft: (data) => {
    return apiClient.patch('/owner/ai-manager/notices/draft', data);
  },

  // 7. 운영 위험 조기정보
  // 조회, 상세 조회, 전력 사용 리포트 조회, 사장 실축값 입력, 절감 계획 생성, 절감 계획 저장
  getRiskEarlyInfo: () => {
    return apiClient.get('/owner/ai-manager/operation-risks');
  },
  getRiskEarlyInfoDetail: () => {
    return apiClient.get(`/owner/ai-manager/operation-risks/detail`);
  },
  getPowerUsageReport: () => {
    return apiClient.get(
      `/owner/ai-manager/operation-risks/electricity-report`,
    );
  },
  submitActualPowerUsage: (data) => {
    return apiClient.post(
      '/owner/ai-manager/operation-risks/owner-input',
      data,
    );
  },
  createReductionPlan: () => {
    return apiClient.post('/owner/ai-manager/operation-risks/saving-plan');
  },
  saveReductionPlan: (data) => {
    return apiClient.post(
      '/owner/ai-manager/operation-risks/saving-plan/save',
      data,
    );
  },

  // 8. 플랜 관리
  // 플랜 조회, 구독 취소, 구독 결제 완료 검증, 구독 결제 요청
  getPlans: () => {
    return apiClient.get('/owner/ai-manager/plans');
  },
  cancelSubscribe: () => {
    return apiClient.post('/owner/ai-manager/plans/cancel');
  },
  completeSubscribe: (paymentId) => {
    return apiClient.post('/owner/ai-manager/plans/payments/complete', {
      paymentId,
    });
  },
  requestSubscribe: (planType) => {
    return apiClient.post('/owner/ai-manager/plans/subscribe', {
      planType,
    });
  },

  // 9. 리뷰/문의 관리
  // 자동 대응 현황 조회, 반복 불만 대응 문구 생성, 공지 초안 생성, 리뷰 답글 초안 생성
  getReviewInquiryStatus: () => {
    return apiClient.get('/owner/ai-manager/reviews-inquiries');
  },
  createComplaintDraft: (data) => {
    return apiClient.post(
      '/owner/ai-manager/reviews-inquiries/complaint-draft',
      data,
    );
  },
  createNoticeDraft: () => {
    return apiClient.patch('/owner/ai-manager/reviews-inquiries/notice-draft');
  },
  createReviewReplyDraft: (reviewId, confirmDelete = false) => {
    return apiClient.post(`/owner/ai-manager/reviews/${reviewId}/reply-draft`, {
      params: { confirmDelete },
    });
  },

  // 10. 테스트
  // FCM 단건 테스트 발송
  sendTestFcm: (data) => {
    return apiClient.post('/owner/ai-manager/test/fcm', data);
  },

  // 11. 이벤트 성과 조회
  getEventPerformance: () => {
    return apiClient.get('/owner/ai-manager/events/performance');
  },
};
