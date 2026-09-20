import { client } from './client';

export interface AiExposedStore {
  storeId: number;
  storeName: string;
  address: string;
  /** 클릭 로그를 남길 때 쓰는 노출 상태 ID */
  exposureStatusId: number;
  interest: string | null;
  /** 노출 1회를 가리키는 추적 ID. 클릭할 때 같이 보내야 노출↔클릭이 이어진다. */
  requestId: string | null;
}

export const aiExposureApi = {
  /**
   * 생활권 매칭으로 노출 중인 가게. 비회원도 조회 가능하고, 노출 대상이 없으면
   * 빈 배열이 온다(정상). 조회 자체가 노출 로그로 기록되므로 필요할 때만 부른다.
   *
   * viewerKey 기준 분당 60회 제한이 걸려 있다(초과 시 AI_RATE_LIMITED).
   */
  getExposedStores: async (region?: string): Promise<AiExposedStore[]> => {
    try {
      const res = await client.get('/ai-exposures/stores', {
        params: region ? { region } : undefined,
      });
      return res.data?.data ?? [];
    } catch (error) {
      // 추천 영역은 홈의 곁다리다. 실패해도 홈 나머지는 그대로 떠야 한다.
      console.error('AI 노출 가게 조회 실패:', error);
      return [];
    }
  },

  /**
   * 노출된 가게를 눌렀을 때 클릭 로그를 남긴다. 전환 추적의 연결 고리다.
   * 분당 30회 제한.
   */
  recordClick: async (exposureStatusId: number, requestId?: string | null): Promise<void> => {
    try {
      await client.post(`/ai-exposures/${exposureStatusId}/click`, null, {
        params: requestId ? { requestId } : undefined,
      });
    } catch (error) {
      // 로그 기록 실패로 가게 화면 이동까지 막으면 안 된다.
      console.error('AI 노출 클릭 기록 실패:', error);
    }
  },
};
