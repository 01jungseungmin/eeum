// 1. 문의 상태 Enum
export const INQUIRY_STATUS = {
  PENDING: 'PENDING',
  IN_PROGRESS: 'IN_PROGRESS',
  ANSWERED: 'ANSWERED',
  COMPLETED: 'COMPLETED',
};

// 2. 문의 상태별 UI 매핑 정보
export const INQUIRY_STATUS_INFO = {
  [INQUIRY_STATUS.PENDING]: { label: '처리대기', type: 'waiting' },
  [INQUIRY_STATUS.IN_PROGRESS]: { label: '처리중', type: 'processing' },
  [INQUIRY_STATUS.ANSWERED]: { label: '답변완료', type: 'done' },
  [INQUIRY_STATUS.COMPLETED]: { label: '답변완료', type: 'done' },
};

// 3. 문의 상태 셀렉트 옵션
export const INQUIRY_STATUS_OPTIONS = [
  { value: 'ALL', label: '전체 상태' },
  { value: INQUIRY_STATUS.PENDING, label: '처리대기' },
  { value: INQUIRY_STATUS.IN_PROGRESS, label: '처리중' },
  { value: INQUIRY_STATUS.ANSWERED, label: '답변완료' },
];

// 4. 영문 카테고리 코드를 한글 라벨로 변환하는 매핑 객체
export const INQUIRY_CATEGORY_MAP = {
  ORDER: '주문 문의',
  STORE: '가게 문의',
  USER: '유저 문의',
  PAYMENT: '결제 문의',
  ACCOUNT: '계정 문의',
  SYSTEM: '시스템 문의',
  ETC: '기타 문의',
};

// 5. 셀렉트 박스용 카테고리 옵션
export const INQUIRY_CATEGORY_OPTIONS = [
  { value: 'ALL', label: '전체 카테고리' },
  { value: 'ORDER', label: '주문 문의' },
  { value: 'STORE', label: '가게 문의' },
  { value: 'USER', label: '유저 문의' },
  { value: 'PAYMENT', label: '결제 문의' },
  { value: 'ACCOUNT', label: '계정 문의' },
  { value: 'SYSTEM', label: '시스템 문의' },
  { value: 'ETC', label: '기타 문의' },
];

// 6. 작성자 / 대상 유형 한글 매핑
export const TARGET_TYPE_MAP = {
  ADMIN: '관리자',
  OWNER: '사장님',
  USER: '일반 회원',
  SYSTEM: '시스템',
};
