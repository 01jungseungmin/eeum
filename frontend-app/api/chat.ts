import { client } from './client';

/** 백엔드 CursorSlice<T> 응답 공통 형태 */
export interface CursorSlice<T> {
  content: T[];
  hasNext: boolean;
  nextCursorValue: string | null;
  nextCursorId: number | null;
}

const toCursorSlice = <T>(raw: any): CursorSlice<T> => ({
  content: raw?.content ?? [],
  hasNext: raw?.hasNext ?? false,
  nextCursorValue: raw?.nextCursorValue ?? null,
  nextCursorId: raw?.nextCursorId ?? raw?.nextCursorRoomId ?? null,
});

export const chatApi = {

  // 1. 내 채팅방 목록 조회 (GET /chat/rooms) - cursorValue+cursorRoomId 기반 커서 페이징
  getRooms: async (
    cursorValue: string | null = null,
    cursorRoomId: number | null = null,
    size: number = 20
  ): Promise<CursorSlice<any>> => {
    try {
      const params: any = { size };
      if (cursorValue != null && cursorRoomId != null) {
        params.cursorValue = cursorValue;
        params.cursorRoomId = cursorRoomId;
      }
      const response = await client.get('/chat/rooms', { params });
      return toCursorSlice(response.data?.data);
    } catch (error) {
      console.error('채팅방 목록 조회 에러:', error);
      return { content: [], hasNext: false, nextCursorValue: null, nextCursorId: null };
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

  // 10. 메시지 목록 조회 (과거 내역) (GET /chat/rooms/{roomId}/messages) - cursorValue+cursorId 기반 커서 페이징
  getPastMessages: async (
    roomId: string | number,
    cursorValue: string | null = null,
    cursorId: number | null = null,
    size: number = 50
  ): Promise<CursorSlice<any>> => {
    try {
      const params: any = { size };
      // 이전 메시지를 부를 때(커서가 있을 때)만 두 값을 함께 보낸다. 하나만 보내면 백엔드가 400을 낸다.
      if (cursorValue != null && cursorId != null) {
        params.cursorValue = cursorValue;
        params.cursorId = cursorId;
      }

      const response = await client.get(`/chat/rooms/${roomId}/messages`, { params });
      return toCursorSlice(response.data?.data);
    } catch (error) {
      console.error('과거 채팅 기록 로딩 에러:', error);
      return { content: [], hasNext: false, nextCursorValue: null, nextCursorId: null };
    }
  },

  // 11. 텍스트 메시지 발송 (REST 폴백용) (POST /chat/rooms/{roomId}/messages)
  sendMessage: async (roomId: string | number, content: string) => {
    const response = await client.post(`/chat/rooms/${roomId}/messages`, { content });
    return response.data;
  },

  // 12. 이미지 메시지 발송 (POST /chat/rooms/{roomId}/messages/image)
  sendImageMessage: async (roomId: string | number, imageUrl: string) => {
    const response = await client.post(`/chat/rooms/${roomId}/messages/image`, { imageUrl });
    return response.data;
  }
};