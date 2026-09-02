package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.notification.entity.NotificationOutbox;
import com.eeum.eeum.domain.notification.enums.OutboxStatus;
import com.eeum.eeum.domain.notification.repository.NotificationOutboxRepository;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * outbox 한 행을 두 인스턴스가 동시에 집는 상황.
 *
 * <p>폴링은 {@code @SchedulerLock}이 한 인스턴스로 좁히지만 그 lease는 시간이 지나면 스스로
 * 풀린다({@code lockAtMostFor}). 배치가 그보다 길어지면 두 인스턴스의 처리 구간이 겹치는데,
 * 그때 중복을 실제로 막는 것은 <b>행 잠금</b>이다. 잠금이 없으면 둘 다 PENDING을 읽어
 * 같은 알림을 두 번 만든다.
 *
 * <p>단위 테스트로는 못 잡는다 — JPA 잠금과 트랜잭션 격리는 실제 DB에서만 재현된다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class NotificationOutboxConcurrencyIntegrationTest extends IntegrationTestSupport {

    private final NotificationOutboxDispatcher dispatcher;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final AccountRepository accountRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    private Long senderId;
    private Long recipientId;
    private Long roomId;
    private Long outboxId;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Account sender = accountRepository.save(Account.createUser(
                "outbox-sender-" + tag + "@test.com", "pw", "보낸이", "보낸이" + tag, "010-1111-2222"));
        Account recipient = accountRepository.save(Account.createUser(
                "outbox-recipient-" + tag + "@test.com", "pw", "받는이", "받는이" + tag, "010-3333-4444"));
        senderId = sender.getAccountId();
        recipientId = recipient.getAccountId();

        ChatRoom room = chatRoomRepository.save(ChatRoom.createGroup(
                sender, ChatRoomType.GROUP, "테스트방", ChatRoomRefType.NONE, null, null));
        roomId = room.getChatroomId();
        chatParticipantRepository.save(ChatParticipant.create(room, sender));
        chatParticipantRepository.save(ChatParticipant.create(room, recipient));

        outboxId = outboxRepository.saveAndFlush(NotificationOutbox.pending(
                NotificationOutboxDispatcher.CHAT_MESSAGE_SENT,
                objectMapper.writeValueAsString(new ChatMessageSentEvent(
                        roomId, "테스트방", senderId, "보낸이", "안녕하세요", 1L, false))))
                .getOutboxId();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        outboxRepository.deleteAll();
        chatParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
    }

    @Test
    void 같은_행을_동시에_집어도_알림은_한_번만_만들어진다() throws Exception {
        // Given: 두 인스턴스가 같은 outbox 행을 집는 상황.
        // When: 첫 처리가 커밋되기 전에 두 번째가 같은 행으로 진입한다.
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        raceOnLock(
                transactionManager,
                "notification_outbox",
                () -> dispatchQuietly(outboxId),
                () -> dispatchQuietly(outboxId),
                secondFailure
        );

        // Then: 두 번째는 잠금을 기다렸다가 DONE을 보고 그냥 돌아간다(raceOnLock이 대기 자체를 검증).
        assertThat(secondFailure.get()).isNull();
        assertThat(notificationRepository.countByAccount_AccountIdAndIsReadFalse(recipientId))
                .as("잠금이 없으면 둘 다 PENDING을 읽어 같은 알림이 두 번 만들어진다")
                .isEqualTo(1);

        NotificationOutbox processed = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(OutboxStatus.DONE);
        // 잠금을 기다린 쪽이 실패로 기록되면 시도 횟수가 올라 정상 처리된 행이 FAILED로 뒤집힌다.
        assertThat(processed.getAttemptCount()).isZero();
    }

    @Test
    void 이미_처리된_행에는_실패를_기록하지_않는다() {
        // Given: 앞선 인스턴스가 처리를 끝낸 행
        dispatchQuietly(outboxId);
        assertThat(outboxRepository.findById(outboxId).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.DONE);

        // When: 뒤늦게 도착한 실패 기록(잠금 대기 중 타임아웃 등)
        dispatcher.recordFailure(outboxId, new IllegalStateException("늦게 도착한 실패"));

        // Then: DONE 행의 시도 횟수를 올리면 다섯 번째에 정상 발송된 알림이 FAILED로 뒤집힌다
        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(outbox.getAttemptCount()).isZero();
    }

    private void dispatchQuietly(Long id) {
        try {
            dispatcher.dispatch(id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
