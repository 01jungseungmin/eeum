package com.eeum.eeum.domain.used.event;

/**
 * 판매자 탈퇴로 예약이 취소됐을 때 발행. 상대가 지정된 예약에서만 발행한다.
 *
 * <p>표시용 값은 발행 트랜잭션 안에서 담는다 — 리스너는 커밋 후 비동기로 돌아
 * 그때 엔티티를 다시 읽으면 LAZY 로딩이 세션 밖에서 터진다.
 */
public record UsedProductReservationCancelledEvent(
        Long usedProductId,
        Long buyerAccountId,
        String productTitle
) {
}
