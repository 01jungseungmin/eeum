package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

/** 중고 게시글 목록 커서. 정렬 키를 문자열로 보관해 여러 정렬을 지원한다. */
public record UsedProductCursor(String sortValue, Long usedProductId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 게시글 ID가 없으면 첫 페이지라 null을 돌려준다.
     *
     * 값만 보내는 것은 막는다. tie-break용 ID가 없으면 정렬 키가 같은 글들 사이에서
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
