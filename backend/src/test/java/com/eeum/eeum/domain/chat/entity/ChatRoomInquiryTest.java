package com.eeum.eeum.domain.chat.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 중고 문의방 생성 규칙 — 순수 도메인 검증.
 *
 * <p>active_ref_key 생성식이 {@code ref_type / ref_id / buyer_account_id / is_active} 네 값에
 * 의존하므로, 팩토리가 그 네 값을 빠짐없이 채우는지가 유일성 보장의 전제다.
 */
class ChatRoomInquiryTest {

    private static final Long BUYER_ID = 7L;
    private static final Long PRODUCT_ID = 25L;

    @Test
    void 문의방은_유일성_키에_필요한_값을_모두_채운다() {
        ChatRoom room = ChatRoom.createPrivateInquiry(buyer(), PRODUCT_ID);

        // 하나라도 null이면 생성식이 NULL을 내고, MySQL UNIQUE는 NULL 중복을 허용해 방어가 사라진다.
        assertThat(room.getType()).isEqualTo(ChatRoomType.PRIVATE);
        assertThat(room.getRefType()).isEqualTo(ChatRoomRefType.USED_PRODUCT);
        assertThat(room.getRefId()).isEqualTo(PRODUCT_ID);
        assertThat(room.getBuyerAccountId()).isEqualTo(BUYER_ID);
        assertThat(room.isActive()).isTrue();
    }

    @Test
    void 문의방은_이름과_지역을_두지_않는다() {
        // 이름은 상대·상품으로 표시하고, 지역은 공개 방 목록(GROUP) 필터용이라 1:1 방과 무관하다.
        ChatRoom room = ChatRoom.createPrivateInquiry(buyer(), PRODUCT_ID);

        assertThat(room.getName()).isNull();
        assertThat(room.getRegion()).isNull();
    }

    @Test
    void 문의방은_대화_전까지_마지막_메시지_시각이_없다() {
        // 내 채팅방 목록은 lastMessageAt DESC NULLS LAST라 대화 없는 방은 맨 뒤에 놓인다.
        ChatRoom room = ChatRoom.createPrivateInquiry(buyer(), PRODUCT_ID);

        assertThat(room.getLastMessageAt()).isNull();
    }

    @Test
    void 문의방은_그룹방이_아니다() {
        // 입장·초대 경로(verifyGroupRoom)가 이 판정으로 제3자 진입을 막는다.
        ChatRoom room = ChatRoom.createPrivateInquiry(buyer(), PRODUCT_ID);

        assertThat(room.isGroup()).isFalse();
        assertThat(room.isStoreRoom()).isFalse();
        assertThat(room.isUsedProductRoom()).isTrue();
    }

    @Test
    void 가게_단톡방은_중고_문의방으로_판정되지_않는다() {
        // 잠금 대상·잠금 키가 이 두 술어로 갈리므로 서로 섞이면 안 된다.
        ChatRoom store = ChatRoom.createGroup(
                buyer(), ChatRoomType.GROUP, "가게 단톡방", ChatRoomRefType.STORE, 3L, null);

        assertThat(store.isStoreRoom()).isTrue();
        assertThat(store.isUsedProductRoom()).isFalse();
        assertThat(store.getBuyerAccountId()).isNull();
    }

    private Account buyer() {
        Account account = Account.createUser(
                "buyer@test.com", "encoded_pw", "구매자", "구매자닉", "010-1111-1111");
        ReflectionTestUtils.setField(account, "accountId", BUYER_ID);
        return account;
    }
}
