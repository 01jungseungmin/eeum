package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 채팅 알림 타입 판정.
 *
 * <p>중고 문의방의 첫 메시지를 구매자가 보냈을 때만 {@code USED_PRODUCT_INQUIRY}로 보낸다.
 * 판매자에게 "문의가 들어왔다"를 알리는 것이 이 타입의 의미이므로, 이후 대화나 판매자가
 * 먼저 말을 건 경우까지 같은 타입으로 보내면 알림 목록에서 문의 유입을 구분할 수 없다.
 *
 * <p>{@code NotificationSettings}가 이 타입을 {@code usedProductEnabled}로 묶어 판정하므로,
 * 타입을 잘못 고르면 사용자의 알림 설정이 의도와 다르게 적용된다.
 */
@ExtendWith(MockitoExtension.class)
class ChatNotificationProcessorTypeTest {

    private static final Long ROOM_ID = 10L;
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final Long PRODUCT_ID = 25L;

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatParticipantRepository chatParticipantRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ChatUnreadService chatUnreadService;
    @Mock NotificationService notificationService;

    @InjectMocks ChatNotificationProcessor processor;

    @Test
    void 중고_문의방의_첫_구매자_메시지는_문의_알림으로_보낸다() {
        givenRoom(inquiryRoom());
        when(chatMessageRepository.countByChatRoom_ChatroomId(ROOM_ID)).thenReturn(1L);

        processor.process(event(BUYER_ID));

        assertThat(capturedType()).isEqualTo(NotificationType.USED_PRODUCT_INQUIRY);
    }

    @Test
    void 첫_메시지_이후_대화는_일반_채팅_알림이다() {
        // 문의 유입 알림은 방당 한 번이어야 알림 목록에서 의미를 갖는다.
        givenRoom(inquiryRoom());
        when(chatMessageRepository.countByChatRoom_ChatroomId(ROOM_ID)).thenReturn(2L);

        processor.process(event(BUYER_ID));

        assertThat(capturedType()).isEqualTo(NotificationType.CHAT_MESSAGE);
    }

    @Test
    void 판매자가_먼저_보낸_메시지는_문의_알림이_아니다() {
        // 구매자에게 "문의가 들어왔다"고 알리는 건 뜻이 맞지 않는다.
        givenRoom(inquiryRoom());

        processor.process(event(SELLER_ID));

        assertThat(capturedType()).isEqualTo(NotificationType.CHAT_MESSAGE);
        // 중고 방이어도 발신자가 판매자면 첫 메시지 판정 쿼리를 돌릴 이유가 없다.
        verify(chatMessageRepository, never()).countByChatRoom_ChatroomId(any());
    }

    @Test
    void 일반_채팅방은_첫_메시지_판정_쿼리를_돌리지_않는다() {
        // count 쿼리가 모든 채팅 알림에 붙으면 중고와 무관한 경로에 비용만 는다.
        givenRoom(groupRoom());

        processor.process(event(BUYER_ID));

        assertThat(capturedType()).isEqualTo(NotificationType.CHAT_MESSAGE);
        verify(chatMessageRepository, never()).countByChatRoom_ChatroomId(any());
    }

    private NotificationType capturedType() {
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotification(captor.capture());
        return captor.getValue().getType();
    }

    private void givenRoom(ChatRoom room) {
        when(chatRoomRepository.findByIdWithPessimisticLock(ROOM_ID)).thenReturn(Optional.of(room));
        // 발신자를 제외한 수신자 한 명에게만 알림이 간다.
        when(chatParticipantRepository.findActiveAccountIds(ROOM_ID))
                .thenReturn(List.of(BUYER_ID, SELLER_ID));
    }

    private ChatRoom inquiryRoom() {
        ChatRoom room = ChatRoom.createPrivateInquiry(account(BUYER_ID), PRODUCT_ID);
        ReflectionTestUtils.setField(room, "chatroomId", ROOM_ID);
        return room;
    }

    private ChatRoom groupRoom() {
        ChatRoom room = ChatRoom.createGroup(
                account(BUYER_ID), ChatRoomType.GROUP, "단톡방", ChatRoomRefType.NONE, null, null);
        ReflectionTestUtils.setField(room, "chatroomId", ROOM_ID);
        return room;
    }

    private ChatMessageSentEvent event(Long senderId) {
        return new ChatMessageSentEvent(ROOM_ID, null, senderId, "보낸사람", "안녕하세요", 100L);
    }

    private Account account(Long accountId) {
        Account account = Account.createUser(
                "u" + accountId + "@test.com", "pw", "사용자", "닉" + accountId, "010-1111-1111");
        ReflectionTestUtils.setField(account, "accountId", accountId);
        return account;
    }
}
