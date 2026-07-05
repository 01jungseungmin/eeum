import { apiClient } from '../apiClient';

export const chatApi = {
  createGroupChat: (chatData) => {
    return apiClient.post('/chat/rooms/group', chatData);
  },

  // 메시지 목록 조회 API ({roomId}와 페이징 쿼리 파라미터 전달)
  getMessages: (roomId, params) => {
    return apiClient.get(`/chat/rooms/${roomId}/messages`, {
      params,
    });
  },
  // 메시지 전송 API (roomId와 메시지 데이터 전달)
  sendMessage: (roomId, messageData) => {
    // messageData 예시: { content: "안녕하세요!", clientMessageId: "uuid-string" }
    return axios.post(`/chat/rooms/${roomId}/messages`, messageData);
  },
};
