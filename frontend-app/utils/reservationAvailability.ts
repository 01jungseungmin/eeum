/**
 * 방문 예약이 막힌 이유가 '상점 자체'인지 '고른 날짜'인지 가른다.
 *
 * 예약 가능 시간대 API는 두 종류의 실패를 같은 모양으로 돌려준다.
 *
 *   상점 단위 — 예약 설정이 없거나, 꺼져 있거나, 상점이 영업 상태가 아니다.
 *               날짜를 아무리 바꿔도 안 된다.
 *   날짜 단위 — 휴무일, 당일예약 불가, 영업시간 미등록.
 *               다른 날짜를 고르면 된다.
 *
 * 상점 단위는 화면에 들어온 순간 알 수 있다. 날짜와 인원을 다 채운 뒤에야
 * "예약 설정을 찾을 수 없습니다"를 보여주면 헛수고를 시키는 셈이다.
 */

// 날짜와 무관하게 이 상점에서는 예약이 안 된다는 뜻인 코드들.
const STORE_LEVEL_CODES = new Set([
  'RESERVATION_006', // 현재 예약할 수 없는 상점입니다 (상점이 OPEN 이 아님)
  'RESERVATION_012', // 예약 설정을 찾을 수 없습니다 (사장이 예약을 열지 않았다)
  'RESERVATION_013', // 해당 상점은 방문 예약 기능을 사용하지 않습니다
]);

export function isStoreLevelReservationBlock(code: string | null): boolean {
  return code !== null && STORE_LEVEL_CODES.has(code);
}

/**
 * 상점 단위로 막혔을 때 덧붙일 한 줄. 서버 문구는 사실만 말하므로
 * 사용자가 다음에 뭘 하면 되는지를 여기서 보탠다.
 */
export function getStoreLevelReservationHint(code: string | null): string {
  if (code === 'RESERVATION_006') {
    return '상점이 영업을 준비 중이에요. 잠시 후 다시 시도해 주세요.';
  }
  return '이 상점은 아직 방문 예약을 받지 않아요. 다른 상점을 둘러봐 주세요.';
}
