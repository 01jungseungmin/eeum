import { apiClient } from '../apiClient';

export const chatApi = {
  // 사장님 단톡방 개설
  createGroupChat: (chatData) => {
    return apiClient.post('/chat/rooms/group', chatData);
  },

  // 채팅방 상세 조회
  getRoomDetail: (roomId) => {
    return apiClient.get(`/chat/rooms/${roomId}`);
  },

  // 메시지 목록 조회
  getMessages: (roomId, params) => {
    return apiClient.get(`/chat/rooms/${roomId}/messages`, {
      params,
    });
  },

  // 메시지 / 이미지 전송
  sendMessage: (roomId, messageData) => {
    // messageData 예시: { content: "안녕하세요!", clientMessageId: "uuid-string" }
    return apiClient.post(`/chat/rooms/${roomId}/messages`, messageData);
  },
  sendImageMessage: (roomId, data) => {
    return apiClient.post(`/chat/rooms/${roomId}/messages/image`, data); // @RequestBody 구조와 일치
  },

  // 메시지 삭제
  deleteMessage: (messageId) => {
    return apiClient.delete(`/chat/messages/${messageId}`);
  },

  // 채팅방 읽음 처리
  markRoomAsRead: (roomId) => {
    return apiClient.patch(`/chat/rooms/${roomId}/read`);
  },

  // 안 읽은 채팅 수 조회
  getUnreadCount: () => {
    return apiClient.get('/chat/messages/unread/count');
  },
};
