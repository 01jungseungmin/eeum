package com.eeum.eeum.domain.chat.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Entity
@Getter
// 변경된 컬럼만 UPDATE한다. 없으면 lastMessageAt만 바꿔도 is_active/closed_at까지 함께 쓰여서,
// 종료 직전에 시작된 메시지 트랜잭션이 커밋될 때 종료된 방이 ACTIVE로 되살아난다(lost update).
// 메시지 발송은 락을 잡지 않으므로 종료와 직렬화할 수단이 없어 이 방어가 필수다.
@DynamicUpdate
@Table(
        name = "chat_room",
        uniqueConstraints = {
                // ACTIVE 상태의 가게 단톡방 중복을 DB 레벨에서 차단 (activeRefKey 주석 참고)
                @UniqueConstraint(name = "uk_chat_room_active_ref", columnNames = {"active_ref_key"})
        },
        indexes = {
                @Index(name = "idx_chat_room_ref", columnList = "ref_type, ref_id, is_active")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long chatroomId;

    // 채팅방 생성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Account creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChatRoomType type;

    // Polymorphic 참조 타입 (TRADE / STORE / COMMUNITY 등) — FK 아님
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 20)
    private ChatRoomRefType refType;

    // 연관 도메인 ID (Polymorphic 참조)
    @Column(name = "ref_id")
    private Long refId;

    // 채팅방 이름 (GROUP 타입에서 사용)
    @Column(name = "name", length = 100)
    private String name;

    // 중고거래 1:1 방의 구매자. USED_PRODUCT 방에서만 채워지고 그 외 방에서는 항상 null이다.
    // 판매자는 UsedProduct.seller로 결정되므로 따로 두지 않는다.
    //
    // FK로 걸지 않는다 — refId와 같은 정책이다. 계정 물리 삭제(AccountCleanupScheduler)가
    // chat_room의 기존 FK에 막히는 문제가 이미 있어, 같은 테이블에 계정 FK를 하나 더 늘리지 않는다.
    // 아래 active_ref_key 생성식이 이 컬럼을 참조하므로 컬럼명을 바꾸면 DDL도 함께 바꿔야 한다.
    @Column(name = "buyer_account_id")
    private Long buyerAccountId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // 채팅방 종료 시각 (isActive=false 전이 시점). 종료 이력 추적 및 재생성 디버깅용
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // ACTIVE 가게 단톡방 중복 방지용 MySQL 생성 컬럼 (애플리케이션에서 쓰지 않음 — 읽기 전용).
    // 종료됐거나 STORE 방이 아니면 NULL이 되고, MySQL UNIQUE는 NULL 중복을 허용하므로
    // 종료된 방은 동일 조합으로 얼마든지 누적될 수 있다 — soft delete 구조와 충돌하지 않는다.
    //
    // [운영 주의] ddl-auto=update는 "컬럼 신규 생성"만 하고 기존 컬럼 정의를 MODIFY하지 않는다.
    // 과거 정의에는 type이 포함돼 있었으므로(CONCAT(ref_type,':',ref_id,':',type)),
    // 그 정의로 컬럼이 이미 만들어진 DB는 아래 수동 DDL로 교체해야 한다:
    //   ALTER TABLE chat_room DROP INDEX uk_chat_room_active_ref;
    //   ALTER TABLE chat_room DROP COLUMN active_ref_key;
    //   ALTER TABLE chat_room ADD COLUMN active_ref_key VARCHAR(80)
    //       GENERATED ALWAYS AS (CASE
    //           WHEN is_active = 1 AND ref_type = 'STORE' AND ref_id IS NOT NULL
    //               THEN CONCAT(ref_type, ':', ref_id)
    //           WHEN is_active = 1 AND ref_type = 'USED_PRODUCT' AND ref_id IS NOT NULL
    //                AND buyer_account_id IS NOT NULL
    //               THEN CONCAT(ref_type, ':', ref_id, ':', buyer_account_id)
    //       END) STORED;
    //   ALTER TABLE chat_room ADD CONSTRAINT uk_chat_room_active_ref UNIQUE (active_ref_key);
    // 또한 최초 반영 시 이미 중복 ACTIVE 방이 있으면 유니크 인덱스 생성이 실패하고
    // ddl-auto=update는 이를 로그만 남기고 넘어가므로, 배포 전 아래로 중복을 정리해야 한다.
    // (생성식에 type이 없으므로 중복 판정도 ref_id만으로 한다 — type까지 묶어 세면 중복을 놓친다):
    //   SELECT ref_id, COUNT(*) FROM chat_room
    //   WHERE is_active = 1 AND ref_type = 'STORE' AND ref_id IS NOT NULL
    //   GROUP BY ref_id HAVING COUNT(*) > 1;
    @Column(
            name = "active_ref_key",
            insertable = false,
            updatable = false,
            // 키에 type을 넣지 않는다 — 넣으면 한 가게가 GROUP과 GROUP_STREET 단톡방을 동시에
            // ACTIVE로 가질 수 있는데, 상점 상세/대시보드는 type 무관 최신 1건을 노출하므로
            // 사장이 보는 방과 고객이 유입되는 방이 갈린다. 가게당 ACTIVE 단톡방은 1개다.
            columnDefinition = "VARCHAR(80) GENERATED ALWAYS AS ("
                    + "CASE WHEN is_active = 1 AND ref_type = 'STORE' AND ref_id IS NOT NULL "
                    + "THEN CONCAT(ref_type, ':', ref_id) "
                    // 중고거래는 "상품 + 구매자"당 ACTIVE 방 1개다. 판매자는 상품에서 결정되므로 키에 넣지 않는다.
                    + "WHEN is_active = 1 AND ref_type = 'USED_PRODUCT' AND ref_id IS NOT NULL "
                    + "AND buyer_account_id IS NOT NULL "
                    + "THEN CONCAT(ref_type, ':', ref_id, ':', buyer_account_id) "
                    + "END) STORED"
    )
    private String activeRefKey;

    // 마지막 메시지 시각 (목록 정렬용)
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    // 지역 기반 공개 방 목록 필터링용 (GROUP/GROUP_STREET — nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    // ===================== 정적 팩토리 메서드 =====================

    /**
     * 중고거래 1:1 문의방.
     *
     * <p>이름을 두지 않는다 — 상대와 상품이 방을 식별하므로 목록·상세에서 그 둘로 표시한다.
     * 지역도 두지 않는다 — 공개 방 목록(GROUP/GROUP_STREET) 필터용 필드라 1:1 방과 무관하다.
     *
     * <p>{@code lastMessageAt}은 첫 메시지 전까지 null이다. 내 채팅방 목록은
     * {@code lastMessageAt DESC NULLS LAST}라 대화 없는 방은 목록 맨 뒤에 놓인다.
     */
    public static ChatRoom createPrivateInquiry(
            Account buyer,
            Long usedProductId
    ) {
        ChatRoom room = new ChatRoom();
        room.creator = buyer;
        room.type = ChatRoomType.PRIVATE;
        room.refType = ChatRoomRefType.USED_PRODUCT;
        room.refId = usedProductId;
        room.buyerAccountId = buyer.getAccountId();
        room.isActive = true;
        return room;
    }

    public static ChatRoom createGroup(
            Account creator,
            ChatRoomType type,
            String name,
            ChatRoomRefType refType,
            Long refId,
            Region region
    ) {
        ChatRoom room = new ChatRoom();
        room.creator = creator;
        room.type = type;
        room.name = name;
        room.refType = refType;
        room.refId = refId;
        room.region = region;
        room.isActive = true;
        return room;
    }

    // ===================== 도메인 메서드 =====================

    // 마지막 메시지 시각 갱신
    public void updateLastMessageAt(LocalDateTime sentAt) {
        this.lastMessageAt = sentAt;
    }

    // 채팅방 비활성화 (GROUP 전체 퇴장 / 사장 종료 / 관리자 강제 종료)
    // 메시지 기록은 보존하고 상태만 전이하는 soft close — 물리 삭제하지 않는다
    public void deactivate() {
        if (!this.isActive) {
            return;
        }
        this.isActive = false;
        this.closedAt = LocalDateTime.now();
    }

    public boolean isGroup() {
        return this.type == ChatRoomType.GROUP || this.type == ChatRoomType.GROUP_STREET;
    }

    // 가게 단톡방 여부 (종료 권한 판정에 사용)
    public boolean isStoreRoom() {
        return this.refType == ChatRoomRefType.STORE && this.refId != null;
    }

    // 중고거래 1:1 문의방 여부 (잠금 대상·종료 권한 판정에 사용)
    public boolean isUsedProductRoom() {
        return this.refType == ChatRoomRefType.USED_PRODUCT && this.refId != null;
    }

    // 채팅방 생성자 여부
    public boolean isCreatedBy(Long accountId) {
        return this.creator.getAccountId().equals(accountId);
    }
}
