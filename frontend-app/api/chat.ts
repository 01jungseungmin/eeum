import { client } from './client';

export const chatApi = {

  // 1. 내 채팅방 목록 조회 (GET /chat/rooms) - 중복 해결 및 페이징 적용!
  getRooms: async (page: number = 0, size: number = 20) => {
    try {
      const response = await client.get('/chat/rooms', { params: { page, size } });
      return response.data?.data?.content || response.data?.data || [];
    } catch (error) {
      console.error('채팅방 목록 조회 에러:', error);
      return [];
    }
  },

  // ✨ [NEW] 새로운 모임 찾기 (전체 오픈 채팅방 목록 조회) (GET /chat/rooms/discover)
  getDiscoverRooms: async (page: number = 0, size: number = 20) => {
    try {
      // 🚨 혹시 백엔드에서 만든 전체 방 목록 주소가 /chat/rooms/discover 가 아니라면 이 부분만 수정해 주세요!
      const response = await client.get('/chat/rooms/discover', { params: { page, size } });
      return response.data?.data?.content || response.data?.data || [];
    } catch (error) {
      console.error('오픈 채팅방 목록 조회 에러:', error);
      throw error; // 에러를 던져서 화면에서 로딩 스피너를 멈출 수 있게 합니다.
    }
  },

  // 2. 채팅방 상세 정보 조회 (GET /chat/rooms/{roomId})
  getRoomDetail: async (roomId: string | number) => {
    try {
      const response = await client.get(`/chat/rooms/${roomId}`);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('채팅방 상세 조회 에러:', error);
      throw error;
    }
  },

  // 3. 채팅방 나가기 (PATCH /chat/rooms/{roomId}/leave)
  leaveRoom: async (roomId: string | number) => {
    const response = await client.patch(`/chat/rooms/${roomId}/leave`);
    return response.data;
  },

  // 4. 채팅방 참여자 초대 (POST /chat/rooms/{roomId}/participants)
  inviteParticipants: async (roomId: string | number, userIds: number[]) => {
    const response = await client.post(`/chat/rooms/${roomId}/participants`, { userIds });
    return response.data;
  },

  // 5. 채팅방 직접 입장 (POST /chat/rooms/{roomId}/participants/me)
  joinRoom: async (roomId: string | number) => {
    const response = await client.post(`/chat/rooms/${roomId}/participants/me`);
    return response.data;
  },

  // 6. 채팅방 읽음 처리 (PATCH /chat/rooms/{roomId}/read)
  markAsRead: async (roomId: string | number) => {
    const response = await client.patch(`/chat/rooms/${roomId}/read`);
    return response.data;
  },

  // 7. 그룹(단톡방) 채팅방 생성 (POST /chat/rooms/group)
  createGroupRoom: async (data: { name: string; description?: string; maxParticipants?: number }) => {
    const response = await client.post('/chat/rooms/group', data);
    return response.data?.data || response.data;
  },


  // 8. 메시지 삭제 (DELETE /chat/messages/{messageId})
  deleteMessage: async (messageId: string | number) => {
    const response = await client.delete(`/chat/messages/${messageId}`);
    return response.data;
  },

  // 9. 전체 안 읽은 채팅 수 (GET /chat/messages/unread/count)
  getUnreadCount: async () => {
    try {
      const response = await client.get('/chat/messages/unread/count');
      return response.data?.data || response.data;
    } catch (error) {
      console.error('안 읽은 채팅 수 조회 에러:', error);
      return 0;
    }
  },

  // 10. 메시지 목록 조회 (과거 내역) (GET /chat/rooms/{roomId}/messages) - 중복 해결!
  getPastMessages: async (roomId: string | number, page: number = 0, size: number = 50) => {
    try {
      const response = await client.get(`/chat/rooms/${roomId}/messages`, {
        params: { page, size }
      });
      return response.data?.data?.content || response.data?.data || [];
    } catch (error) {
      console.error('과거 채팅 기록 로딩 에러:', error);
      return [];
    }
  },

  // 11. 텍스트 메시지 발송 (REST 폴백용) (POST /chat/rooms/{roomId}/messages)
  sendMessage: async (roomId: string | number, content: string) => {
    const response = await client.post(`/chat/rooms/${roomId}/messages`, { content });
    return response.data;
  },

  // 12. 이미지 메시지 발송 (POST /chat/rooms/{roomId}/messages/image)
  sendImageMessage: async (roomId: string | number, imageUrls: string[]) => {
    const response = await client.post(`/chat/rooms/${roomId}/messages/image`, { imageUrls });
    return response.data;
  }
};