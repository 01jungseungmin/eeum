// 관리자 알림 발송/이력 도메인 상수 (domain/notification/enums/NotificationType.java)
export const NOTIFICATION_TYPE_LABEL = {
  ORDER_STATUS_CHANGED: '주문 상태 변경',
  PAYMENT_COMPLETED: '결제 완료',
  NEW_ORDER: '새 주문 접수',
  RESERVATION_CONFIRMED: '예약 확정',
  RESERVATION_CANCELLED: '예약 취소',
  RESERVATION_REJECTED: '예약 거절',
  RESERVATION_REMINDER: '예약 리마인드',
  NEW_RESERVATION: '새 방문 예약',
  CHAT_MESSAGE: '새 채팅 메시지',
  COMMUNITY_COMMENT: '커뮤니티 댓글',
  COMMUNITY_REPLY: '커뮤니티 대댓글',
  COMMUNITY_LIKE: '커뮤니티 좋아요',
  COMMUNITY_ADMIN_ACTION: '커뮤니티 관리자 조치',
  STORE_REVIEW: '새 리뷰',
  STORE_REVIEW_REPLY: '리뷰 답글',
  STORE_REVIEW_ADMIN_ACTION: '리뷰 관리자 조치',
  STORE_PRODUCT_RESTOCK: '관심 상품 재입고',
  STOCK_WARNING: '재고 부족 경고',
  SETTLEMENT_COMPLETED: '정산 완료',
  USED_PRODUCT_INQUIRY: '중고거래 문의',
  USED_REVIEW: '중고거래 후기 요청',
  INQUIRY_ANSWERED: '문의 답변 완료',
  SYSTEM_NOTICE: '시스템 공지',
  MARKETING_EVENT: '마케팅/이벤트',
  OWNER_APPLICATION_SUBMITTED: '사장 승인 신청',
  REPORT_SUBMITTED: '신고 접수',
  INQUIRY_SUBMITTED: '문의 접수',
  OPERATION_FAILURE_DETECTED: '운영 실패 감지',
};

// 발송 이력 필터 드롭다운 — 실제 이력에 남는 모든 알림 타입을 노출하되,
// 개수가 많아 훑어보기 어려우므로 도메인별로 묶어서 보여준다 (<optgroup> 렌더링용)
export const NOTIFICATION_TYPE_FILTER_GROUPS = [
  {
    label: '주문/결제',
    types: ['ORDER_STATUS_CHANGED', 'PAYMENT_COMPLETED', 'NEW_ORDER'],
  },
  {
    label: '예약',
    types: [
      'RESERVATION_CONFIRMED',
      'RESERVATION_CANCELLED',
      'RESERVATION_REJECTED',
      'RESERVATION_REMINDER',
      'NEW_RESERVATION',
    ],
  },
  {
    label: '채팅',
    types: ['CHAT_MESSAGE'],
  },
  {
    label: '커뮤니티',
    types: [
      'COMMUNITY_COMMENT',
      'COMMUNITY_REPLY',
      'COMMUNITY_LIKE',
      'COMMUNITY_ADMIN_ACTION',
    ],
  },
  {
    label: '리뷰',
    types: ['STORE_REVIEW', 'STORE_REVIEW_REPLY', 'STORE_REVIEW_ADMIN_ACTION'],
  },
  {
    label: '상품/재고',
    types: ['STORE_PRODUCT_RESTOCK', 'STOCK_WARNING'],
  },
  {
    label: '정산/중고거래',
    types: ['SETTLEMENT_COMPLETED', 'USED_PRODUCT_INQUIRY', 'USED_REVIEW'],
  },
  {
    label: '문의',
    types: ['INQUIRY_SUBMITTED', 'INQUIRY_ANSWERED'],
  },
  {
    label: '공지/마케팅',
    types: ['SYSTEM_NOTICE', 'MARKETING_EVENT'],
  },
  {
    label: '신고/운영',
    types: [
      'OWNER_APPLICATION_SUBMITTED',
      'REPORT_SUBMITTED',
      'OPERATION_FAILURE_DETECTED',
    ],
  },
];
