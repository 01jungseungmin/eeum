package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증상: private S3 objectKey가 응답과 STOMP payload에 그대로 실려 이미지를 표시할 수 없었다.
 * 채팅 STOMP 브로드캐스트는 HTTP ResponseBodyAdvice를 거치지 않으므로,
 * 리스너가 두 이미지 필드에 URL 변환을 적용할 수 있도록 DTO 변환 함수를 제공한다.
 */
class ChatMessageResponseDtoTest {

    @Test
    void 메시지_이미지와_보낸이_프로필을_모두_조회용_URL로_바꾼다() {
        // Given
        Map<String, String> signed = Map.of(
                "chat/2/message.webp", "https://signed.example/message",
                "profiles/2/profile.webp", "https://signed.example/profile");
        ChatMessage message = imageMessage("chat/2/message.webp", "profiles/2/profile.webp");

        // When
        ChatMessageResponseDto dto = ChatMessageResponseDto.from(
                message, key -> signed.getOrDefault(key, key));

        // Then — 둘 중 하나만 바꾸면 상대 프로필이 깨진 이미지로 남는다.
        assertThat(dto.getImageUrl()).isEqualTo("https://signed.example/message");
        assertThat(dto.getSenderProfileImageUrl()).isEqualTo("https://signed.example/profile");
    }

    @Test
    void 삭제된_메시지는_이미지를_내려보내지_않는다() {
        // Given
        ChatMessage message = imageMessage("chat/2/message.webp", null);
        message.markDeleted();

        // When
        ChatMessageResponseDto dto = ChatMessageResponseDto.from(message, key -> "https://signed.example/x");

        // Then
        assertThat(dto.getImageUrl()).isNull();
        assertThat(dto.isDeleted()).isTrue();
    }

    private ChatMessage imageMessage(String imageObjectKey, String profileObjectKey) {
        Account sender = Account.createUser(
                "sender@test.com", "encoded_pw", "이름", "닉네임", "010-1111-2222");
        ReflectionTestUtils.setField(sender, "accountId", 2L);
        if (profileObjectKey != null) {
            sender.updateInfo(null, profileObjectKey);
        }
        ChatRoom room = ChatRoom.createGroup(sender, ChatRoomType.GROUP, "방", null, null, null);
        ReflectionTestUtils.setField(room, "chatroomId", 20L);
        return ChatMessage.image(room, sender, imageObjectKey);
    }
}
