import { client } from './client';

export const chatApi = {
  // 1. 내 채팅방 목록 조회
  getRooms: async () => {
    const response = await client.get('/chat/rooms');
    return response.data;
  },

  // 2. 채팅방 상세 정보 조회
  getRoomDetail: async (roomId: string | number) => {
    const response = await client.get(`/chat/rooms/${roomId}`);
    return response.data;
  },

  // 3. 채팅방 메시지 목록 조회
  getMessages: async (roomId: string | number) => {
    const response = await client.get(`/chat/rooms/${roomId}/messages`);
    return response.data;
  },

  // 4. 텍스트 메시지 발송 (REST 폴백용)
  sendMessage: async (roomId: string | number, content: string) => {
    const response = await client.post(`/chat/rooms/${roomId}/messages`, { content });
    return response.data;
  },

  // 5. 채팅방 읽음 처리
  markAsRead: async (roomId: string | number) => {
    const response = await client.patch(`/chat/rooms/${roomId}/read`);
    return response.data;
  }
};