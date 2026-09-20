// 관리자 채팅 관리 도메인 상수 (백엔드 enum과 1:1 매칭)
// domain/chat/enums/ChatRoomType.java
export const CHAT_ROOM_TYPE_LABEL = {
  PRIVATE: '1:1 채팅',
  GROUP: '단체 채팅',
  GROUP_STREET: '동네 채팅',
};

export const CHAT_ROOM_TYPE_FILTER_OPTIONS = [
  { value: '', label: '전체 타입' },
  { value: 'PRIVATE', label: '1:1 채팅' },
  { value: 'GROUP', label: '단체 채팅' },
  { value: 'GROUP_STREET', label: '동네 채팅' },
];

// domain/chat/enums/ChatRoomRefType.java
export const CHAT_ROOM_REF_TYPE_LABEL = {
  USED_PRODUCT: '중고거래',
  STORE: '상점',
  COMMUNITY: '커뮤니티',
  NONE: '일반',
};

export const CHAT_ROOM_ACTIVE_FILTER_OPTIONS = [
  { value: '', label: '전체 상태' },
  { value: 'true', label: '활성' },
  { value: 'false', label: '비활성' },
];
