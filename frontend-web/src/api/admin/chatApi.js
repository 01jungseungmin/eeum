import { apiClient } from '../apiClient';

// 관리자 채팅 모니터링/모더레이션 API (AdminChatController)
export const chatApi = {
  // 전체 채팅방 조회 (type/isActive/from/to 필터 + 페이지네이션)
  getAllRooms: (params) => apiClient.get('/admin/chat/rooms', { params }),

  // 특정 채팅방 메시지 조회
  getRoomMessages: (roomId, params) =>
    apiClient.get(`/admin/chat/rooms/${roomId}/messages`, { params }),

  // 메시지 강제 삭제 (Soft Delete)
  forceDeleteMessage: (messageId) =>
    apiClient.delete(`/admin/chat/messages/${messageId}`),

  // 채팅방 강제 비활성화
  forceDeactivateRoom: (roomId) =>
    apiClient.delete(`/admin/chat/rooms/${roomId}`),
};
