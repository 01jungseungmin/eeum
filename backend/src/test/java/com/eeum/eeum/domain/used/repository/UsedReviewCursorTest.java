package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 후기 목록 커서 파라미터 해석.
 *
 * <p>커서는 정렬 키 두 개(createdAt, usedReviewId)를 함께 받아야 한다.
 * 하나만 왔을 때 조용히 첫 페이지로 처리하면, 클라이언트는 다음 페이지를 요청했다고 믿는데
 * 서버는 같은 목록을 돌려주므로 무한 스크롤이 제자리에서 반복된다.
 */
class UsedReviewCursorTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final String CREATED_AT_VALUE = CREATED_AT.toString();

    @Test
    void 둘_다_없으면_첫_페이지다() {
        assertThat(UsedReviewCursor.ofNullable(null, null)).isNull();
    }

    @Test
    void 둘_다_있으면_커서를_만든다() {
        UsedReviewCursor cursor = UsedReviewCursor.ofNullable(CREATED_AT_VALUE, 41L);

        assertThat(cursor).isNotNull();
        assertThat(cursor.createdAt()).isEqualTo(CREATED_AT);
        assertThat(cursor.usedReviewId()).isEqualTo(41L);
    }

    @Test
    void 작성일시만_보내면_거절한다() {
        assertThatThrownBy(() -> UsedReviewCursor.ofNullable(CREATED_AT_VALUE, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_INVALID_CURSOR);
    }

    @Test
    void 값의_형식이_어긋나면_거절한다() {
        // 응답의 nextCursorValue를 그대로 돌려보내는 계약이라, 형식이 깨졌다는 것은
        // 클라이언트가 값을 직접 만들었거나 다른 목록의 커서를 보냈다는 뜻이다.
        assertThatThrownBy(() -> UsedReviewCursor.ofNullable("어제", 41L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_INVALID_CURSOR);
    }

    @Test
    void 후기_ID만_보내면_거절한다() {
        assertThatThrownBy(() -> UsedReviewCursor.ofNullable(null, 41L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_REVIEW_INVALID_CURSOR);
    }
}
