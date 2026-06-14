import { client } from './client'; 

export interface RegionInfo {
  accountRegionId: number;
  regionId: number;
  siDo: string;
  gunGu: string;
  dong: string;
  isPrimary: boolean;
}

export interface MyInfoResponse {
  accountId: number;
  email: string;
  name: string;
  nickname: string;
  phone?: string;
  profileImageUrl?: string;
  regions?: RegionInfo[];
  introduction?: string;         // 자기소개 문구
  receivedReviewCount?: number;  // 받은 리뷰 수
  sentReviewCount?: number;      // 보낸 리뷰 수
  tradeCount?: number;           // 거래 횟수
}



export const userApi = {
  getMyInfo: async (): Promise<MyInfoResponse> => {
    const response = await client.get('/accounts/me'); 
    
    return response.data?.data || response.data; 
  },

  // 회원 정보 수정 (닉네임, 프로필 이미지)
  updateProfile: async (data: { nickname: string; profileImageUrl: string }) => {
    const response = await client.patch('/accounts/me', data);
    return response.data;
  },

  // 비밀번호 변경
  updatePassword: async (data: { reAuthToken: string; currentPassword: string; newPassword: string }) => {
    const response = await client.patch('/accounts/me/password', data);
    return response.data;
  },

  reauth: async (data: { password?: string; oauthToken?: string }) => {
    try {
      const response = await client.post('/auth/reauth', data);
      // 백엔드 구조(success, data, message)에 맞춰 data 영역을 리턴합니다.
      return response.data?.data || response.data; 
    } catch (error) {
      console.error('재인증 토큰 발급 에러:', error);
      throw error;
    }
  },

  deleteAccount: async (data: { reAuthToken: string }) => {
    try {
      const response = await client.delete('/accounts/me', { data });
      return response.data;
    } catch (error) {
      console.error('회원 탈퇴 에러:', error);
      throw error;
    }
  }
};