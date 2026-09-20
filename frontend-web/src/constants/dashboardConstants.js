// 가입자 추이 탭 라벨 ↔ 백엔드 SignupMemberType
export const SIGNUP_TYPE_TABS = [
  { label: '일반', value: 'GENERAL' },
  { label: '사장', value: 'OWNER' },
];

// 백엔드 DayOfWeek → 한글 요일
export const DAY_OF_WEEK_LABEL = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
};

// 대시보드 실시간 활동 종류(DashboardActivityType) 별 제목
export const ACTIVITY_TITLE = {
  MEMBER_SIGNUP: '새 회원 가입',
  STORE_REGISTERED: '신규 가게 등록',
  PAYMENT_COMPLETED: '거래 완료',
  REPORT_RECEIVED: '플랫폼 신고 접수',
};
