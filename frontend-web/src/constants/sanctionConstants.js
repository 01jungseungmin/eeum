// 관리자 제재 이력 도메인 상수 (domain/sanction/enums/*.java)
export const SANCTION_TARGET_TYPE_LABEL = {
  ACCOUNT: '회원',
  STORE: '상점',
};

export const SANCTION_ACTION_LABEL = {
  WARN: '경고',
  SUSPEND: '정지',
  ACTIVATE: '정지 해제',
};

export const SANCTION_SOURCE_LABEL = {
  DIRECT_ADMIN: '관리자 직접 조치',
  REPORT: '신고 처리 결과',
};
