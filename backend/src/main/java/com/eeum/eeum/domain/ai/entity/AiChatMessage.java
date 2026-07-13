package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.enums.AiChatRole;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_chat_message")
// AI 매니저 챗봇 대화 기록 저장
public class AiChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_chat_message_id")
    private Long aiChatMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id", nullable = false)
    private Account ownerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AiChatRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public static AiChatMessage create(Store store, Account ownerAccount, AiChatRole role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.store = store;
        message.ownerAccount = ownerAccount;
        message.role = role;
        message.content = content;
        return message;
    }
}
