// 백엔드 Enum -> 한글 라벨 매핑 객체
export const TARGET_TYPE_MAP = {
  STORE: '가게',
  STORE_REVIEW: '가게 리뷰',
  COMMUNITY_POST: '게시글',
  COMMUNITY_COMMENT: '댓글',
  ACCOUNT: '유저',
};

export const REASON_MAP = {
  SPAM: '스팸 / 광고',
  ABUSE: '욕설 및 비방',
  FRAUD: '사기 및 기만',
  INAPPROPRIATE_CONTENT: '부적절한 콘텐츠',
  FALSE_INFORMATION: '허위 정보',
  PERSONAL_INFORMATION: '개인정보 노출',
  ETC: '기타',
};

export const STATUS_MAP = {
  PENDING: '대기중',
  REVIEWED: '검토 완료',
  DISMISSED: '기각됨',
};

// UI 컴포넌트용 필터 옵션
export const TYPE_TABS = [
  { label: '전체', value: 'ALL' },
  { label: '가게', value: 'STORE' },
  { label: '가게 리뷰', value: 'STORE_REVIEW' },
  { label: '게시글', value: 'COMMUNITY_POST' },
  { label: '댓글', value: 'COMMUNITY_COMMENT' },
  { label: '유저', value: 'ACCOUNT' },
];

export const STATUS_FILTER_OPTIONS = [
  { label: '전체 상태', value: 'ALL' },
  { label: '대기중', value: 'PENDING' },
  { label: '검토 완료', value: 'REVIEWED' },
  { label: '기각됨', value: 'DISMISSED' },
];

// 신고 처리 조치 옵션
export const ACTION_OPTIONS = [
  { label: '게시글 숨김', value: 'HIDE_POST' },
  { label: '게시글 삭제', value: 'DELETE_POST' },
  { label: '작성자 경고', value: 'WARN_USER' },
  { label: '작성자 정지', value: 'SUSPEND_USER' },
  { label: '신고 기각', value: 'DISMISS' },
];
