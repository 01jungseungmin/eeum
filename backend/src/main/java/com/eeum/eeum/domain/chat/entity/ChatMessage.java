package com.eeum.eeum.domain.chat.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.enums.MessageType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_message",
        indexes = {
                // 방 메시지 목록 — 방으로 좁히고 최신순으로 읽는다. 커서 조건과 정렬이
                // 모두 이 인덱스로 풀린다. PK를 명시해야 같은 sent_at 구간의 정렬까지 인덱스가 맡는다.
                @Index(name = "idx_chat_message_room_sent",
                        columnList = "chat_room_id, sent_at, chat_message_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long chatmessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    // 발신자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // 메시지 내용 (TEXT/SYSTEM 타입)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // 이미지 URL (IMAGE 타입)
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    // ===== LOCATION 타입 전용 =====
    // 채팅은 참여자에게만 보이므로, 공개되는 게시글과 달리 정확한 주소까지 담는다.
    // 다른 타입에서는 전부 null이다 — 아래 팩토리가 애초에 값을 받지 않아 구조적으로 보장된다.

    // 표시용 장소명 (예: "○○역 3번 출구")
    @Column(name = "place_name", length = 255)
    private String placeName;

    // 지번/도로명 주소. 카카오 검색을 거치지 않은 핀은 없을 수 있다.
    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // 카카오 장소 ID. 나중에 장소 상세를 재조회할 때 쓴다.
    @Column(name = "place_id", length = 50)
    private String placeId;

    // 발신자 본인 삭제 시각 (Soft Delete — null이면 미삭제)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 발신 시각 (인덱스 대상)
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    // ===================== 정적 팩토리 메서드 =====================

    public static ChatMessage text(ChatRoom chatRoom, Account sender, String content) {
        return create(chatRoom, sender, content, null, MessageType.TEXT);
    }

    public static ChatMessage image(ChatRoom chatRoom, Account sender, String imageUrl) {
        return create(chatRoom, sender, null, imageUrl, MessageType.IMAGE);
    }

    public static ChatMessage system(ChatRoom chatRoom, Account actor, String content) {
        return create(chatRoom, actor, content, null, MessageType.SYSTEM);
    }

    /**
     * 거래 장소 제안 메시지.
     *
     * <p>여기 담기는 값은 <b>제안</b>이지 확정된 약속이 아니다. 마지막 LOCATION 메시지가
     * 곧 합의된 장소는 아니므로(상대가 거절했을 수 있다), 확정 장소는 별도로 관리한다.
     *
     * <p>{@code address}와 {@code placeId}는 카카오 장소 검색을 거치지 않고 지도에서
     * 직접 찍은 핀이면 없을 수 있어 선택값이다.
     */
    public static ChatMessage location(
            ChatRoom chatRoom,
            Account sender,
            String placeName,
            Double latitude,
            Double longitude,
            String address,
            String placeId
    ) {
        validateLocation(placeName, latitude, longitude);

        ChatMessage message = create(chatRoom, sender, null, null, MessageType.LOCATION);
        message.placeName = placeName;
        message.latitude = latitude;
        message.longitude = longitude;
        // 선택값의 빈 문자열은 "없음"으로 통일한다. 저장해봐야 의미가 없고,
        // 다른 타입의 위치 컬럼이 NULL이어야 한다는 규칙과 판정이 갈린다.
        message.address = blankToNull(address);
        message.placeId = blankToNull(placeId);
        return message;
    }

    private static ChatMessage create(
            ChatRoom chatRoom,
            Account account,
            String content,
            String imageUrl,
            MessageType messageType
    ) {
        ChatMessage message = new ChatMessage();
        message.chatRoom = chatRoom;
        message.account = account;
        message.content = content;
        message.imageUrl = imageUrl;
        message.messageType = messageType;
        message.sentAt = LocalDateTime.now();
        return message;
    }

    /**
     * 위치 메시지의 필수값과 좌표 범위를 확인한다.
     *
     * <p>프론트에서 카카오 검색 결과만 고르도록 막아도 API를 직접 호출하면 임의 좌표가
     * 들어온다. 클라이언트 제약은 UX일 뿐이므로 범위 검증은 서버가 맡는다.
     */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static void validateLocation(String placeName, Double latitude, Double longitude) {
        if (placeName == null || placeName.isBlank() || latitude == null || longitude == null) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID_LOCATION);
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID_LOCATION);
        }
    }

    // ===================== 도메인 메서드 =====================

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // Soft Delete — 삭제 시각 기록
    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }

    // 발신자 본인 여부
    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }

    // 본인 발신 + 미삭제 + 시스템 메시지가 아닐 때만 삭제 가능
    public boolean isDeletable(Long accountId) {
        return isOwnedBy(accountId) && deletedAt == null && this.messageType != MessageType.SYSTEM;
    }
}
