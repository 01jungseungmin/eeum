import { apiClient } from '../apiClient';

export const approvalApi = {
  // 입점 심사 요청 (최종 제출)
  applyOwnerStoreApproval: () => {
    return apiClient.post('/owner/stores/me/apply');
  },

  // 입점 심사용 상점 영업시간 입력/수정
  updateOwnerStoreBusinessHours: (businessHoursData) => {
    return apiClient.put(
      '/owner/stores/me/approval/business-hours',
      businessHoursData,
    );
  },

  // 입점 심사용 상점 기본 정보 입력/수정 (사업자 정보, 상호명 등)
  updateOwnerStoreBusinessInfo: (businessInfoData) => {
    return apiClient.patch('/owner/stores/me/business-info', businessInfoData);
  },

  // 입점 심사 체크리스트 조회 (화면 하단의 체크리스트 및 반려 사유 상태 조회용)
  getOwnerStoreChecklist: () => {
    return apiClient.get('/owner/stores/me/checklist');
  },

  // 대표 메뉴 등록/수정
  updateRepresentativeMenu: (menuData) => {
    return apiClient.put('/owner/stores/me/representative-menu', menuData);
  },

  // 정산 계좌 등록/수정
  updateOwnerStoreSettlementAccount: (accountData) => {
    return apiClient.put('/owner/stores/me/settlement-account', accountData);
  },
};
