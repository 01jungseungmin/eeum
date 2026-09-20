// 관리자 상점 관리 도메인 상수 (백엔드 enum과 1:1 매칭)
// domain/store/enums/StoreStatus.java
export const STORE_STATUS_LABEL = {
  OPEN: '영업중',
  TEMP_CLOSED: '휴업',
  CLOSED: '폐업',
  SUSPENDED: '관리자 정지',
};

export const STORE_STATUS_FILTER_OPTIONS = [
  { value: '', label: '전체 상태' },
  { value: 'OPEN', label: '영업중' },
  { value: 'TEMP_CLOSED', label: '휴업' },
  { value: 'CLOSED', label: '폐업' },
  { value: 'SUSPENDED', label: '관리자 정지' },
];

// domain/account/enums/ApprovalStatus.java (입점 승인 상태)
export const STORE_APPROVAL_STATUS_LABEL = {
  PENDING: '승인 대기 중',
  APPROVED: '승인 완료',
  REJECTED: '승인 거부',
};
