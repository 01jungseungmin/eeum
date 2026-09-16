package com.eeum.eeum.domain.used.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 중고거래 후기의 도메인 불변식.
 *
 * <p>별점 범위는 엔티티가 강제한다. 요청 단계 {@code @Min/@Max}만 두면 HTTP 밖에서 들어오는
 * 경로(내부 호출·스케줄러)에 검증이 비어 있게 된다.
 */
class UsedReviewTest {

    private static final Long SELLER_ID = 1L;
    private static final Long REVIEWER_ID = 2L;

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void 별점은_1에서_5까지_허용한다(int rating) {
        assertThatCode(() -> UsedReview.create(product(), reviewer(), rating, "잘 받았습니다"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 100})
    void 범위를_벗어난_별점은_거부한다(int rating) {
        assertThatThrownBy(() -> UsedReview.create(product(), reviewer(), rating, "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 수정하면_별점과_내용이_함께_바뀐다() {
        UsedReview review = UsedReview.create(product(), reviewer(), 5, "좋아요");

        review.update(3, "다시 보니 보통이에요");

        assertThat(review.getRating()).isEqualTo(3);
        assertThat(review.getContent()).isEqualTo("다시 보니 보통이에요");
    }

    @Test
    void 수정에도_같은_별점_범위가_적용된다() {
        // 작성만 막고 수정을 열어두면 우회 경로가 된다.
        UsedReview review = UsedReview.create(product(), reviewer(), 5, "좋아요");

        assertThatThrownBy(() -> review.update(9, "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);

        assertThat(review.getRating()).as("검증에 걸리면 기존 값이 유지돼야 한다").isEqualTo(5);
    }

    @Test
    void 작성자_판정은_계정_ID로_한다() {
        UsedReview review = UsedReview.create(product(), reviewer(), 5, "좋아요");

        assertThat(review.isWrittenBy(REVIEWER_ID)).isTrue();
        assertThat(review.isWrittenBy(SELLER_ID)).isFalse();
    }

    private UsedProduct product() {
        return UsedProduct.create(
                account(SELLER_ID, "seller"),
                Category.createRoot(CategoryType.USED, "디지털기기", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                "자전거 팝니다", "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
    }

    private Account reviewer() {
        return account(REVIEWER_ID, "buyer");
    }

    private Account account(Long accountId, String prefix) {
        Account account = Account.createUser(
                prefix + "@test.com", "encoded-pw", "사용자", prefix + "닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", accountId);
        return account;
    }
}
