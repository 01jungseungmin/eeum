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
  // 아래 네 개는 GET /accounts/me(MyPageResponseDto)가 주지 않는다.
  // 프로필 통계는 getMyProfileStats()로 따로 모은다. 자기소개는 아직 백엔드에
  // 저장할 곳이 없어 항상 비어 있다.
  introduction?: string;         // 자기소개 문구
  receivedReviewCount?: number;  // 받은 리뷰 수
  sentReviewCount?: number;      // 보낸 리뷰 수
  tradeCount?: number;           // 거래 횟수
}

/** 프로필 화면에 띄우는 활동 통계. 소스가 제각각이라 화면에서 합쳐 쓴다. */
export interface ProfileStats {
  receivedReviewCount: number;
  sentReviewCount: number;
  tradeCount: number;
}

// 주문은 서버가 status 필터를 받지 않아(OrderController.getMyOrders는 Pageable만 받는다)
// 전부 받아서 여기서 센다. 페이지가 많아도 이 정도면 충분하고, 무한 루프도 막는다.
const ORDER_PAGE_SIZE = 100;
const ORDER_MAX_PAGES = 5;

// 통계 하나가 실패해도 프로필은 떠야 한다. 실패한 항목만 0으로 둔다.
const countOrZero = async (label: string, load: () => Promise<number>): Promise<number> => {
  try {
    return await load();
  } catch (error) {
    console.warn(`프로필 통계(${label}) 조회 실패`, error);
    return 0;
  }
};

/** 받은 리뷰 — 중고거래에서 내가 판매자로서 받은 후기. 서버가 개수를 바로 준다. */
const fetchReceivedReviewCount = (accountId: number) =>
  countOrZero('받은 리뷰', async () => {
    const response = await client.get(`/used/sellers/${accountId}/reviews/summary`);
    const summary = response.data?.data ?? response.data;
    return Number(summary?.reviewCount) || 0;
  });

/** 보낸 리뷰 — 내가 쓴 상점·예약 리뷰. 목록은 필요 없으니 size=1로 총개수만 받는다. */
const fetchSentReviewCount = () =>
  countOrZero('보낸 리뷰', async () => {
    const response = await client.get('/reviews/me', { params: { page: 0, size: 1 } });
    const page = response.data?.data ?? response.data;
    return Number(page?.totalElements) || 0;
  });

/** 거래 횟수 — 거래까지 끝난(COMPLETED) 주문. 취소·만료·미수령은 세지 않는다. */
const fetchCompletedOrderCount = () =>
  countOrZero('거래 횟수', async () => {
    let completed = 0;

    for (let page = 0; page < ORDER_MAX_PAGES; page += 1) {
      const response = await client.get('/orders', {
        params: { page, size: ORDER_PAGE_SIZE },
      });
      const body = response.data?.data ?? response.data;
      const orders: any[] = body?.content ?? [];

      completed += orders.filter((order) => order?.status === 'COMPLETED').length;

      // last 가 없는 응답도 있어 내용으로도 끊는다.
      if (body?.last === true || orders.length < ORDER_PAGE_SIZE) break;
    }

    return completed;
  });



export const userApi = {
  getMyInfo: async (): Promise<MyInfoResponse> => {
    const response = await client.get('/accounts/me');

    return response.data?.data || response.data;
  },

  /**
   * 프로필 활동 통계.
   *
   * /accounts/me 가 이 숫자들을 주지 않아서(MyPageResponseDto에 필드 자체가 없다)
   * 이미 있는 조회 API 세 개를 모아 만든다. 백엔드를 고치지 않아도 앱과 웹 데모가
   * 똑같이 동작하는 쪽을 택했다 — 데모는 운영 백엔드를 그대로 쓰기 때문에,
   * 백엔드를 고치면 그쪽을 재배포하기 전까지는 계속 0으로 보인다.
   *
   * 셋 중 하나가 실패해도 나머지는 보여준다. 통계 하나 때문에 프로필 전체가
   * 안 뜨면 손해가 더 크다.
   */
  getMyProfileStats: async (accountId: number): Promise<ProfileStats> => {
    const [received, sent, trades] = await Promise.all([
      fetchReceivedReviewCount(accountId),
      fetchSentReviewCount(),
      fetchCompletedOrderCount(),
    ]);

    return { receivedReviewCount: received, sentReviewCount: sent, tradeCount: trades };
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