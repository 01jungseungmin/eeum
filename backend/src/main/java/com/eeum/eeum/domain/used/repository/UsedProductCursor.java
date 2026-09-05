package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

/**
 * 중고 게시글 목록 커서 — 직전 페이지의 마지막 글 위치.
 *
 * <p>OFFSET을 쓰지 않는 이유가 이 타입의 존재 이유다. 기본 정렬이 최신순이라 새 글이 맨 앞에
 * 꽂힌다. 1페이지를 읽고 2페이지를 요청하는 사이 한 건이 등록되면 목록 전체가 한 칸 밀려,
 * 경계에 있던 글이 2페이지에서 다시 나온다. 중고 목록은 후기보다 새 글 유입이 훨씬 잦아
 * 이 중복이 실제로 자주 보인다.
 *
 * <p><b>값을 문자열로 들고 다니는 이유.</b> 정렬 키를 클라이언트가 고를 수 있어
 * (createdAt·price·favoriteCount·viewCount) 커서에 담기는 값의 타입도 함께 바뀐다.
 * 어떤 타입으로 읽을지는 실제 적용된 정렬을 아는 리포지토리가 정하므로, 여기서는 원문만 보관한다.
 *
 * <p>{@code sortValue}는 null일 수 있다 — 가격순 정렬에서 가격제안(price null) 글에 걸린 커서다.
 * 그 구간은 정렬상 맨 뒤이므로 ID로만 이어 읽는다.
 *
 * <p><b>한계.</b> 찜 수·조회수처럼 계속 변하는 키로 정렬하면 행 자체가 커서를 넘나들어
 * 중복·누락이 남는다. 커서는 "페이지 사이 삽입에 목록이 통째로 밀리는" 문제를 없앨 뿐,
 * 정렬 키가 변하는 문제까지 없애지는 못한다.
 */
public record UsedProductCursor(String sortValue, Long usedProductId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 게시글 ID가 없으면 첫 페이지라 {@code null}을 돌려준다.
     *
     * <p>값만 보내는 것은 막는다. tie-break용 ID가 없으면 정렬 키가 같은 글들 사이에서
     * 경계를 끊지 못해 OFFSET과 똑같은 중복·누락이 생기는데, 조용히 첫 페이지를 돌려주면
     * 무한 스크롤이 같은 목록을 반복하게 된다.
     */
    public static UsedProductCursor ofNullable(String sortValue, Long usedProductId) {
        // 첫 페이지는 "둘 다 보내지 않은" 경우뿐이다. 값만 보냈다면(빈 값이라도) 커서를 보냈다고
        // 믿는 클라이언트에게 첫 페이지를 돌려주는 셈이라 목록이 반복된다.
        if (sortValue == null && usedProductId == null) {
            return null;
        }
        if (usedProductId == null) {
            throw new BadRequestException(ErrorCode.USED_PRODUCT_INVALID_CURSOR);
        }
        // 게시글 ID와 함께 온 빈 값은 정상이다 — 정렬 키가 NULL인 구간(가격제안 글)을 가리킨다.
        return new UsedProductCursor(
                sortValue == null || sortValue.isBlank() ? null : sortValue.trim(), usedProductId);
    }
}
