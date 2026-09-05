package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 찜 목록 커서 — 직전 페이지의 마지막 찜 위치.
 *
 * <p>세 목록(전체·상점·중고)이 같은 커서를 쓴다. 정렬이 모두 {@code favorite.createdAt desc,
 * favoriteId desc}로 같기 때문이다. 목록이 달라도 커서를 만드는 규칙이 하나라, 클라이언트는
 * 어느 탭에서든 직전 응답의 값을 그대로 되돌려보내면 된다.
 *
 * <p>OFFSET을 쓰지 않는 이유가 이 타입의 존재 이유다. 찜 목록은 최신순이라 새 찜이 맨 앞에
 * 꽂힌다. 스크롤 도중 하나를 더 찜하면 목록 전체가 밀려 경계 항목이 다음 페이지에서 중복되고,
 * 반대로 찜을 해제하면 한 건이 건너뛰어진다. 찜은 목록을 보는 중에 토글하는 것이 정상적인
 * 사용 흐름이라 이 흔들림이 특히 자주 일어난다.
 *
 * <p>{@code favoriteId}까지 담는 이유는 같은 순간에 여러 건이 등록될 수 있어서다
 * (탭을 옮기며 연속으로 찜하면 초 단위가 겹친다). PK로 끊지 않으면 그 구간에서
 * OFFSET과 같은 중복·누락이 재현된다.
 */
public record FavoriteCursor(LocalDateTime createdAt, Long favoriteId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 둘 다 보내지 않으면 첫 페이지라 {@code null}을 돌려준다.
     *
     * <p>한쪽만 보내는 것은 막는다 — 빈 문자열도 "보낸 것"으로 본다. 조용히 첫 페이지를
     * 돌려주면 클라이언트는 다음 페이지를 받았다고 믿는데 같은 목록이 다시 쌓인다.
     */
    public static FavoriteCursor ofNullable(String cursorValue, Long favoriteId) {
        if (cursorValue == null && favoriteId == null) {
            return null;
        }
        if (favoriteId == null || cursorValue == null || cursorValue.isBlank()) {
            throw new BadRequestException(ErrorCode.FAVORITE_INVALID_CURSOR);
        }
        return new FavoriteCursor(parseCreatedAt(cursorValue.trim()), favoriteId);
    }

    /**
     * 커서 값은 응답의 {@code nextCursorValue}를 그대로 되돌려보낸 것이다. 형식이 어긋났다는 것은
     * 클라이언트가 값을 직접 만들었거나 다른 목록의 커서를 보냈다는 뜻이라 조용히 넘기지 않는다.
     */
    private static LocalDateTime parseCreatedAt(String rawValue) {
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(ErrorCode.FAVORITE_INVALID_CURSOR);
        }
    }
}
