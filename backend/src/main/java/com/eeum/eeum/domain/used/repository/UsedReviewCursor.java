package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 후기 목록 커서 — 직전 페이지의 마지막 행 위치.
 *
 * <p>OFFSET을 쓰지 않는 이유가 이 타입의 존재 이유다. 후기 목록은 최신순이라 새 후기가
 * <b>맨 앞에</b> 꽂힌다. 1페이지를 읽고 2페이지를 요청하는 사이 한 건이 등록되면 목록 전체가
 * 한 칸씩 밀려, 경계에 있던 후기가 2페이지에서 다시 나온다(삭제되면 반대로 한 건이 사라진다).
 * 커서는 "몇 번째부터"가 아니라 "이 행 다음부터"를 가리키므로 그 사이 삽입·삭제에 흔들리지 않는다.
 *
 * <p>정렬 키가 {@code createdAt desc, usedReviewId desc} 두 개라 커서도 두 값을 함께 가진다.
 * {@code createdAt}만 쓰면 같은 시각에 등록된 후기들 사이에서 경계를 끊지 못해
 * OFFSET과 같은 중복·누락이 그대로 재현된다(초 단위가 겹치는 벌크 등록에서 실제로 난다).
 */
public record UsedReviewCursor(LocalDateTime createdAt, Long usedReviewId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 둘 다 없으면 첫 페이지라 {@code null}을 돌려준다.
     *
     * <p>하나만 보내는 것은 막는다. 조용히 무시하면 클라이언트는 커서를 보냈다고 믿는데
     * 서버는 첫 페이지를 돌려주므로, 무한 스크롤이 같은 목록을 영원히 반복한다.
     * 빈 문자열도 "보낸 것"으로 본다 — 후기의 작성일시는 NULL일 수 없어 빈 커서 값이 성립하지 않는다.
     *
     * <p>값은 문자열로 받는다 — 응답의 {@code nextCursorValue}를 그대로 되돌려보내는 계약이라
     * 목록마다 다른 타입을 클라이언트가 알 필요가 없다.
     */
    public static UsedReviewCursor ofNullable(String cursorValue, Long usedReviewId) {
        // 첫 페이지는 "둘 다 보내지 않은" 경우뿐이다. ?cursorValue= 처럼 빈 값이라도 보냈다면
        // 클라이언트는 커서를 보냈다고 믿고 있으므로, 첫 페이지를 돌려주면 목록이 반복된다.
        if (cursorValue == null && usedReviewId == null) {
            return null;
        }
        if (usedReviewId == null || cursorValue == null || cursorValue.isBlank()) {
            throw new BadRequestException(ErrorCode.USED_REVIEW_INVALID_CURSOR);
        }
        return new UsedReviewCursor(parseCreatedAt(cursorValue.trim()), usedReviewId);
    }

    /**
     * 커서 값은 응답의 {@code nextCursorValue}를 그대로 되돌려보낸 것이다. 형식이 어긋났다는 것은
     * 클라이언트가 값을 직접 만들었거나 다른 목록의 커서를 보냈다는 뜻이라 조용히 넘기지 않는다.
     */
    private static LocalDateTime parseCreatedAt(String rawValue) {
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(ErrorCode.USED_REVIEW_INVALID_CURSOR);
        }
    }
}
