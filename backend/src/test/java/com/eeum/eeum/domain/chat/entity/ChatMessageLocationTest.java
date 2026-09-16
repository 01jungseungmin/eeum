package com.eeum.eeum.domain.chat.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.enums.MessageType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 위치 메시지 규칙 테스트.
 *
 * <p>외부 의존성이 없는 순수 엔티티 테스트라 Mock을 쓰지 않는다.
 *
 * <p>여기서 검증하는 핵심은 두 방향이다. LOCATION은 장소명·좌표가 반드시 있어야 하고,
 * 그 밖의 타입에는 위치 값이 실리면 안 된다. 후자는 타입별 정적 팩토리가 위치 인자를
 * 받지 않으므로 구조적으로 보장되는데, 팩토리를 늘릴 때 깨질 수 있어 함께 고정한다.
 */
class ChatMessageLocationTest {

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long USED_PRODUCT_ID = 10L;

    private static final String PLACE_NAME = "역삼역 3번 출구";
    private static final double LATITUDE = 37.500622;
    private static final double LONGITUDE = 127.036456;

    // ─────────────────── 생성 ───────────────────

    @Test
    void 장소명과_좌표가_있으면_위치_메시지가_생성된다() {
        // when
        ChatMessage message = ChatMessage.location(
                room(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE,
                "서울 강남구 역삼동 823", "26338954");

        // then
        assertThat(message.getMessageType()).isEqualTo(MessageType.LOCATION);
        assertThat(message.getPlaceName()).isEqualTo(PLACE_NAME);
        assertThat(message.getLatitude()).isEqualTo(LATITUDE);
        assertThat(message.getLongitude()).isEqualTo(LONGITUDE);
        assertThat(message.getAddress()).isEqualTo("서울 강남구 역삼동 823");
        assertThat(message.getPlaceId()).isEqualTo("26338954");
    }

    @Test
    void 주소와_장소_ID는_없어도_된다() {
        // given — 카카오 검색을 거치지 않고 지도에서 직접 찍은 핀은 둘 다 없다

        // when
        ChatMessage message = ChatMessage.location(
                room(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE, null, null);

        // then
        assertThat(message.getMessageType()).isEqualTo(MessageType.LOCATION);
        assertThat(message.getAddress()).isNull();
        assertThat(message.getPlaceId()).isNull();
    }

    @Test
    void 위치_메시지는_본문을_쓰지_않는다() {
        // given — 지도 말풍선은 장소명으로 렌더링한다. content에 값이 남으면
        // 클라이언트가 텍스트와 지도 중 무엇을 그릴지 알 수 없다.

        // when
        ChatMessage message = ChatMessage.location(
                room(), buyer(), PLACE_NAME, LATITUDE, LONGITUDE, null, null);

        // then
        assertThat(message.getContent()).isNull();
        assertThat(message.getImageUrl()).isNull();
    }

    // ─────────────────── 필수값 ───────────────────

    @Test
    void 장소명이_없으면_위치_메시지를_보낼_수_없다() {
        assertLocationRejected(null, LATITUDE, LONGITUDE);
    }

    @Test
    void 공백_장소명은_이름이_없는_것으로_보고_막는다() {
        assertLocationRejected("   ", LATITUDE, LONGITUDE);
    }

    @Test
    void 위도가_없으면_위치_메시지를_보낼_수_없다() {
        assertLocationRejected(PLACE_NAME, null, LONGITUDE);
    }

    @Test
    void 경도가_없으면_위치_메시지를_보낼_수_없다() {
        assertLocationRejected(PLACE_NAME, LATITUDE, null);
    }

    // ─────────────────── 좌표 범위 ───────────────────

    @Test
    void 위도가_범위를_벗어나면_보낼_수_없다() {
        // given — 프론트를 카카오 검색 결과로 제한해도 API 직접 호출로 임의 좌표가 들어온다.
        // 클라이언트 제약은 UX일 뿐이라 범위 검증은 서버가 맡는다.

        // when & then
        assertLocationRejected(PLACE_NAME, 90.1, LONGITUDE);
        assertLocationRejected(PLACE_NAME, -90.1, LONGITUDE);
    }

    @Test
    void 경도가_범위를_벗어나면_보낼_수_없다() {
        assertLocationRejected(PLACE_NAME, LATITUDE, 180.1);
        assertLocationRejected(PLACE_NAME, LATITUDE, -180.1);
    }

    @Test
    void 경계값은_허용한다() {
        // given — 남극점·날짜변경선은 유효한 좌표다. 경계를 닫으면 정상 입력이 막힌다.

        // when & then
        assertThat(ChatMessage.location(room(), buyer(), PLACE_NAME, 90.0, 180.0, null, null))
                .extracting(ChatMessage::getMessageType)
                .isEqualTo(MessageType.LOCATION);
        assertThat(ChatMessage.location(room(), buyer(), PLACE_NAME, -90.0, -180.0, null, null))
                .extracting(ChatMessage::getMessageType)
                .isEqualTo(MessageType.LOCATION);
    }

    // ─────────────────── 다른 타입에는 위치가 실리지 않는다 ───────────────────

    @Test
    void 텍스트_이미지_시스템_메시지에는_위치가_비어_있다() {
        // given & when
        ChatMessage text = ChatMessage.text(room(), buyer(), "안녕하세요");
        ChatMessage image = ChatMessage.image(room(), buyer(), "https://example.com/a.png");
        ChatMessage system = ChatMessage.system(room(), seller(), "입장했습니다");

        // then
        assertLocationEmpty(text);
        assertLocationEmpty(image);
        assertLocationEmpty(system);
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void assertLocationRejected(String placeName, Double latitude, Double longitude) {
        assertThatThrownBy(() -> ChatMessage.location(
                room(), buyer(), placeName, latitude, longitude, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID_LOCATION);
    }

    private void assertLocationEmpty(ChatMessage message) {
        assertThat(message.getPlaceName()).isNull();
        assertThat(message.getAddress()).isNull();
        assertThat(message.getLatitude()).isNull();
        assertThat(message.getLongitude()).isNull();
        assertThat(message.getPlaceId()).isNull();
    }

    private ChatRoom room() {
        return ChatRoom.createPrivateInquiry(buyer(), USED_PRODUCT_ID);
    }

    private Account buyer() {
        Account account = Account.createUser(
                "buyer@test.com", "encoded-pw", "구매자", "구매자닉", "010-1111-1111");
        ReflectionTestUtils.setField(account, "accountId", BUYER_ID);
        return account;
    }

    private Account seller() {
        Account account = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", SELLER_ID);
        return account;
    }
}
