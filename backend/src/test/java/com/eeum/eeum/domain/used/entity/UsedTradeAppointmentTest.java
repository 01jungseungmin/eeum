package com.eeum.eeum.domain.used.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 확정 거래 장소 도메인 규칙 테스트.
 *
 * <p>외부 의존성이 없는 순수 엔티티 테스트라 Mock을 쓰지 않는다.
 */
class UsedTradeAppointmentTest {

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;

    private static final String PLACE_NAME = "역삼역 3번 출구";
    private static final double LATITUDE = 37.500622;
    private static final double LONGITUDE = 127.036456;

    // ─────────────────── 생성 ───────────────────

    @Test
    void 장소명과_좌표가_있으면_약속이_생성된다() {
        // when
        UsedTradeAppointment appointment = UsedTradeAppointment.create(
                product(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE,
                "서울 강남구 역삼동 823", "26338954");

        // then
        assertThat(appointment.getPlaceName()).isEqualTo(PLACE_NAME);
        assertThat(appointment.getLatitude()).isEqualTo(LATITUDE);
        assertThat(appointment.getLongitude()).isEqualTo(LONGITUDE);
        assertThat(appointment.getAddress()).isEqualTo("서울 강남구 역삼동 823");
        assertThat(appointment.getPlaceId()).isEqualTo("26338954");
    }

    @Test
    void 주소와_장소_ID는_없어도_된다() {
        // given — 카카오 검색을 거치지 않고 지도에서 직접 찍은 핀은 둘 다 없다

        // when
        UsedTradeAppointment appointment = UsedTradeAppointment.create(
                product(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE, null, null);

        // then
        assertThat(appointment.getAddress()).isNull();
        assertThat(appointment.getPlaceId()).isNull();
    }

    @Test
    void 장소명이_없으면_약속을_만들_수_없다() {
        // given — 게시글의 대략 위치와 달리 확정 장소는 비워둘 수 없다.
        // "여기서 만나기로 했다"를 담는 값이라 이름과 좌표가 없으면 존재할 이유가 없다.

        // when & then
        assertPlaceRejected(null, LATITUDE, LONGITUDE);
        assertPlaceRejected("   ", LATITUDE, LONGITUDE);
    }

    @Test
    void 좌표가_없으면_약속을_만들_수_없다() {
        assertPlaceRejected(PLACE_NAME, null, LONGITUDE);
        assertPlaceRejected(PLACE_NAME, LATITUDE, null);
    }

    @Test
    void 좌표가_범위를_벗어나면_약속을_만들_수_없다() {
        // given — 프론트를 카카오 검색 결과로 제한해도 API 직접 호출로 임의 좌표가 들어온다

        // when & then
        assertPlaceRejected(PLACE_NAME, 90.1, LONGITUDE);
        assertPlaceRejected(PLACE_NAME, LATITUDE, -180.1);
    }

    @Test
    void 판매자를_구매자로_지정할_수_없다() {
        // given — 자기 거래에 약속을 잡는 경로가 생긴다

        // when & then
        assertThatThrownBy(() -> UsedTradeAppointment.create(
                product(), seller(), PLACE_NAME, LATITUDE, LONGITUDE, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_TRADE_APPOINTMENT_INVALID_PARTY);
    }

    // ─────────────────── 재조율 ───────────────────

    @Test
    void 재조율하면_기존_약속이_새_장소로_바뀐다() {
        // given — 거래당 한 건이므로 새 행을 만들지 않고 이 행을 고친다
        UsedTradeAppointment appointment = UsedTradeAppointment.create(
                product(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE, "옛 주소", "26338954");

        // when
        appointment.relocate("선릉역 1번 출구", 37.504503, 127.048933, null, null);

        // then
        assertThat(appointment.getPlaceName()).isEqualTo("선릉역 1번 출구");
        assertThat(appointment.getLatitude()).isEqualTo(37.504503);
        assertThat(appointment.getLongitude()).isEqualTo(127.048933);
        // 옛 주소·장소 ID가 남으면 새 장소와 어긋난 값이 함께 내려간다
        assertThat(appointment.getAddress()).isNull();
        assertThat(appointment.getPlaceId()).isNull();
    }

    @Test
    void 재조율에서도_빈_장소는_막는다() {
        // given — 생성만 막고 재조율을 열어두면 같은 값이 수정 경로로 들어온다
        UsedTradeAppointment appointment = UsedTradeAppointment.create(
                product(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE, null, null);

        // when & then
        assertThatThrownBy(() -> appointment.relocate(null, LATITUDE, LONGITUDE, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_TRADE_APPOINTMENT_INVALID_PLACE);
    }

    // ─────────────────── 당사자 판정 ───────────────────

    @Test
    void 판매자와_구매자만_거래_당사자다() {
        // given
        UsedTradeAppointment appointment = UsedTradeAppointment.create(
                product(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE, null, null);

        // when & then
        assertThat(appointment.isPartyOf(SELLER_ID)).isTrue();
        assertThat(appointment.isPartyOf(BUYER_ID)).isTrue();
        assertThat(appointment.isPartyOf(999L)).isFalse();
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void assertPlaceRejected(String placeName, Double latitude, Double longitude) {
        assertThatThrownBy(() -> UsedTradeAppointment.create(
                product(), buyer(), placeName, latitude, longitude, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_TRADE_APPOINTMENT_INVALID_PLACE);
    }

    private UsedProduct product() {
        return UsedProduct.create(
                seller(),
                Category.createRoot(CategoryType.USED, "디지털기기", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                "제목", "본문", UsedProductPriceType.FIXED, new BigDecimal("10000"));
    }

    private Account seller() {
        Account account = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", SELLER_ID);
        return account;
    }

    private Account buyer() {
        Account account = Account.createUser(
                "buyer@test.com", "encoded-pw", "구매자", "구매자닉", "010-1111-1111");
        ReflectionTestUtils.setField(account, "accountId", BUYER_ID);
        return account;
    }
}
