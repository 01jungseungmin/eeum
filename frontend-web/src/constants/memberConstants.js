// 관리자 회원 관리 도메인 상수 (백엔드 enum과 1:1 매칭)

// domain/account/enums/AccountRole.java
export const MEMBER_ROLE_LABEL = {
  ROLE_USER: '일반 회원',
  ROLE_OWNER: '사장 회원',
  ROLE_ADMIN: '관리자',
};

// domain/account/enums/AccountStatus.java
export const MEMBER_STATUS_LABEL = {
  PENDING: '가입 미완료',
  ACTIVE: '활성',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴',
};

// domain/account/enums/OAuthProvider.java (LOCAL 은 자체 가입)
export const MEMBER_PROVIDER_LABEL = {
  LOCAL: '이메일 가입',
  KAKAO: '카카오',
  NAVER: '네이버',
};

// 탭 ↔ 회원 목록 조회 조건 (탈퇴 탭은 별도 API 를 쓴다)
export const MEMBER_TAB_QUERY = {
  all: {},
  general: { role: 'ROLE_USER' },
  owner: { role: 'ROLE_OWNER' },
  suspended: { status: 'SUSPENDED' },
};
