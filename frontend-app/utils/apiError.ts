/**
 * 서버가 준 실패 사유를 사용자에게 그대로 보여주기 위한 도우미.
 *
 * 백엔드는 실패를 항상 같은 모양으로 내려준다 (ApiResponse / GlobalExceptionHandler):
 *
 *   { "success": false, "error": { "code": "REVIEW_003", "message": "이미 리뷰를 작성했습니다" } }
 *
 * 화면들이 이걸 버리고 "등록에 실패했습니다" 같은 뭉뚱그린 문구만 띄우면,
 * 사용자는 무엇을 고쳐야 하는지 알 수 없고 개발자도 콘솔을 열기 전엔 원인을 모른다.
 */

export interface ApiErrorInfo {
  code: string | null;
  message: string;
}

// axios 에러만 오는 게 아니다 (코드 버그로 인한 TypeError 등도 같은 catch로 들어온다).
// 그래서 형태를 가정하지 않고 있는 것만 꺼낸다.
export function getApiError(error: any, fallbackMessage: string): ApiErrorInfo {
  const response = error?.response;

  // 응답 자체가 없으면 요청이 서버에 닿지 못한 것이다 — 네트워크·CORS·타임아웃.
  // 이때 error.message는 "Network Error" 같은 영문이라 그대로 보여주지 않는다.
  if (!response) {
    if (error?.code === 'ECONNABORTED') {
      return { code: 'TIMEOUT', message: '서버 응답이 너무 늦어요. 잠시 후 다시 시도해 주세요.' };
    }
    if (error?.request) {
      return { code: 'NETWORK', message: '서버에 연결하지 못했어요. 네트워크 상태를 확인해 주세요.' };
    }
    return { code: null, message: fallbackMessage };
  }

  const body = response.data;
  const detail = body?.error;

  if (detail?.message) {
    return { code: detail.code ?? null, message: detail.message };
  }

  // 일부 경로는 error 없이 message만 준다.
  if (typeof body?.message === 'string' && body.message.length > 0) {
    return { code: null, message: body.message };
  }

  // 본문이 비었거나 우리 형식이 아닌 경우 (502, 프록시 오류 등). 상태 코드라도 남긴다.
  return { code: String(response.status), message: fallbackMessage };
}

/** 알림창에 바로 넣을 문구. 서버 사유가 없으면 fallback 을 쓴다. */
export function getApiErrorMessage(error: any, fallbackMessage: string): string {
  return getApiError(error, fallbackMessage).message;
}
