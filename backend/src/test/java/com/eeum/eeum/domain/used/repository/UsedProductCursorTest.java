package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 중고 게시글 목록 커서 파라미터 해석.
 *
 * <p>정렬 키를 클라이언트가 고를 수 있어 커서 값의 타입도 함께 바뀐다. 여기서는 원문만 보관하고
 * 해석은 실제 적용된 정렬을 아는 리포지토리가 한다. 이 타입이 지키는 것은
 * "tie-break용 게시글 ID 없이는 커서가 성립하지 않는다"는 규칙 하나다.
 */
class UsedProductCursorTest {

    @Test
    void 둘_다_없으면_첫_페이지다() {
        assertThat(UsedProductCursor.ofNullable(null, null)).isNull();
    }

    @Test
    void 값과_ID가_있으면_커서를_만든다() {
        UsedProductCursor cursor = UsedProductCursor.ofNullable("2026-09-01T10:00", 41L);

        assertThat(cursor).isNotNull();
        assertThat(cursor.sortValue()).isEqualTo("2026-09-01T10:00");
        assertThat(cursor.usedProductId()).isEqualTo(41L);
    }

    @Test
    void 값이_비면_정렬_키가_NULL인_구간을_가리킨다() {
        // 가격순 정렬에서 가격제안(price null) 글 구간이다. 담을 값이 없으므로 ID로만 이어 읽는다.
        UsedProductCursor cursor = UsedProductCursor.ofNullable("  ", 41L);

        assertThat(cursor).isNotNull();
        assertThat(cursor.sortValue()).isNull();
        assertThat(cursor.usedProductId()).isEqualTo(41L);
    }

    @Test
    void 빈_값만_보내도_첫_페이지가_아니다() {
        // 가격제안 글 구간의 커서는 값이 비지만 게시글 ID가 함께 온다.
        // ID 없이 빈 값만 오면 커서를 보냈다고 믿는 클라이언트가 첫 페이지를 반복해서 받는다.
        assertThatThrownBy(() -> UsedProductCursor.ofNullable("", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_CURSOR);
    }

    @Test
    void 값만_보내면_거절한다() {
        // tie-break용 ID가 없으면 정렬 키가 같은 글들 사이에서 경계를 끊지 못한다.
        assertThatThrownBy(() -> UsedProductCursor.ofNullable("2026-09-01T10:00", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_CURSOR);
    }
}
