package com.eeum.eeum.domain.used.event;

/**
 * 중고 게시글이 판매완료로 전이됐을 때 발행. 구매자가 지정된 거래에서만 발행한다 —
 * 상대가 없는 거래(앱 밖에서 성사돼 상태만 정리한 글)에는 후기를 쓸 사람이 없다.
 *
 * <p>표시용 값은 발행 트랜잭션 안에서 미리 담는다. 리스너는 커밋 후 비동기로 도므로
 * 그때 엔티티를 다시 읽으면 LAZY 로딩이 세션 밖에서 터진다.
 */
public record UsedProductSoldEvent(
        Long usedProductId,
        Long buyerAccountId,
        String productTitle
) {
}
