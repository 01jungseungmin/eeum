package com.eeum.eeum.infrastructure.alimtalk;

/**
 * 카카오 알림톡 발송 어댑터.
 * 알림톡은 승인된 템플릿 기반 발송이므로 templateCode가 필수다 —
 * AI가 생성한 문구는 템플릿 변수(제목/본문)에 매핑해서 전달한다.
 * local/test는 MockAlimtalkAdapter, 운영은 인증 정보가 설정된 경우에만 실제 Adapter를 사용한다.
 */
public interface AlimtalkAdapter {

    AlimtalkResult send(AlimtalkMessage message);
}
