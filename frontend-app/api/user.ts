import { client } from './client'; 

interface MyInfoResponse {
  accountId: number;
  email: string;
  name: string;
  nickname: string;
  phone?: string;
}

export const userApi = {
  getMyInfo: async (): Promise<MyInfoResponse> => {
    const response = await client.get('/accounts/me'); 
    
    return response.data?.data || response.data; 
  }
};