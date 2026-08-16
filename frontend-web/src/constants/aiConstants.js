// 백엔드 com.eeum.eeum.domain.ai.enums 패키지의 Enum -> 한글 라벨 매핑 객체

// AiCareType — AI 고객 케어 카드 유형
export const AI_CARE_TYPE_MAP = {
  CART_INTEREST: '구매 관심이 높은 고객',
  INACTIVE_REGULAR: '한동안 방문이 없는 단골',
  INQUIRY_HESITATION: '문의 후 망설이는 고객',
};

// AiRiskLevel — 운영 위험 조기정보 신호
export const AI_RISK_LEVEL_MAP = {
  NORMAL: '정상',
  CAUTION: '주의',
  WARNING: '경고',
};

// 위험 신호별 배지 색상 (배경 / 글자)
export const AI_RISK_LEVEL_COLOR = {
  NORMAL: { bg: '#e6f7ee', color: '#00a651' },
  CAUTION: { bg: '#fffbe6', color: '#faad14' },
  WARNING: { bg: '#fff1f0', color: '#ff4d4f' },
};

// AiMessageStatus — AI 생성 메시지 발송 상태
export const AI_MESSAGE_STATUS_MAP = {
  DRAFT: '초안',
  REVIEWED: '검토 완료',
  SENT: '발송 완료',
  SCHEDULED: '예약 대기',
  FAILED: '발송 실패',
  CANCELLED: '취소',
};

// AiMessageType — AI 생성 메시지 유형
export const AI_MESSAGE_TYPE_MAP = {
  CUSTOMER_CARE: '고객 케어',
  REVIEW_REPLY: '리뷰 답글',
  INQUIRY_REPLY: '문의 답변',
  COMPLAINT_REPLY: '불만 대응',
  NOTICE: '공지',
  EVENT_MARKETING: '이벤트 마케팅',
  LOCAL_MATCH: '생활권 매칭',
  RISK_GUIDE: '운영 위험 가이드',
  SAVING_PLAN: '절감 계획',
};

// AiChannel — 발송 채널
export const AI_CHANNEL_MAP = {
  APP_PUSH: '앱 푸시',
  KAKAO_ALERT: '카카오 알림톡',
  STORE_NOTICE: '상점 공지',
  SNS_CARD: 'SNS 카드',
};

// AiTone — 문구 톤
export const AI_TONE_MAP = {
  POLITE: '정중한 톤',
  FRIENDLY: '친근한 톤',
  SHORT: '짧고 간결한 톤',
};

// AiPlanType — AI 플랜
export const AI_PLAN_TYPE_MAP = {
  FREE: 'Free',
  BASIC: 'AI Basic',
  PRO: 'AI Pro',
};

// UI 컴포넌트용 필터 옵션
export const AI_MESSAGE_TYPE_TABS = [
  { label: '전체', value: 'ALL' },
  ...Object.entries(AI_MESSAGE_TYPE_MAP).map(([value, label]) => ({
    label,
    value,
  })),
];

export const AI_TONE_OPTIONS = Object.entries(AI_TONE_MAP).map(
  ([value, label]) => ({ label, value }),
);

// 값이 없을 때 화면에 노출할 기본 문구
export const AI_EMPTY_TEXT = '데이터가 아직 충분하지 않아요';
