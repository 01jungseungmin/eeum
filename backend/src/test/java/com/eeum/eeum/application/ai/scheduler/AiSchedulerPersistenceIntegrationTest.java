package com.eeum.eeum.application.ai.scheduler;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiChatMessage;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiChatRole;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiChatMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 스케줄러/예약 발송 처리 영속성 통합 테스트 — 실제 MySQL Testcontainer 환경에서 실행.
 *
 * 단위 테스트(Mock)로는 검증 불가한 지점만 다룬다:
 * - AiCleanupScheduler의 @Modifying bulk delete가 보관기간 기준으로 실제 행을 지우는지
 * - AiPlanExpirationScheduler의 save 호출 없는 dirty checking 변경이 커밋되는지
 * - AiScheduledMessageProcessor가 트랜잭션 경계 밖에서 호출돼도 LAZY 연관
 *   (store/ownerAccount) 접근 포함 상태 전이가 DB에 반영되는지
 *
 * 예약 메시지는 백그라운드 AiScheduledMessageScheduler(1분 주기)가 집어가지 않도록
 * scheduledAt을 미래로 두고, 검사 시점(now 파라미터)만 그 이후로 밀어서 검증한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AiSchedulerPersistenceIntegrationTest extends IntegrationTestSupport {



    private final AiCleanupScheduler aiCleanupScheduler;
    private final AiPlanExpirationScheduler aiPlanExpirationScheduler;
    private final AiScheduledMessageProcessor aiScheduledMessageProcessor;
    private final JdbcTemplate jdbcTemplate;

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiActionLogRepository aiActionLogRepository;

    private Account owner;
    private Store store;

    @BeforeEach
    void setUp() {
        owner = accountRepository.save(
                Account.createOwner("ai-scheduler-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        store = storeRepository.save(
                Store.createForOwnerSignup(owner, "AI 스케줄러 상점", "서울시", "02-0000-0000"));
    }

    @AfterEach
    void tearDown() {
        aiActionLogRepository.deleteAll();
        aiGeneratedMessageRepository.deleteAll();
        aiChatMessageRepository.deleteAll();
        aiPlanSubscriptionRepository.deleteAll();
        storeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ──────────────────── AiCleanupScheduler (@Modifying bulk delete) ────────────────────

    @Test
    void 보관기간_90일이_지난_AI_채팅_메시지만_bulk_delete로_삭제된다() {
        // given — 저장 후 created_at을 91일 전으로 되돌린다 (JPA Auditing은 저장 시점을 강제하므로 SQL로 백데이트)
        AiChatMessage oldMessage = aiChatMessageRepository.save(
                AiChatMessage.create(store, owner, AiChatRole.USER, "오래된 대화"));
        AiChatMessage recentMessage = aiChatMessageRepository.save(
                AiChatMessage.create(store, owner, AiChatRole.ASSISTANT, "최근 대화"));
        jdbcTemplate.update(
                "UPDATE ai_chat_message SET created_at = ? WHERE ai_chat_message_id = ?",
                LocalDateTime.now().minusDays(91), oldMessage.getAiChatMessageId());

        // when
        aiCleanupScheduler.cleanupOldAiChatMessages();

        // then — 재조회로 실제 삭제 여부 검증
        assertThat(aiChatMessageRepository.findById(oldMessage.getAiChatMessageId())).isEmpty();
        assertThat(aiChatMessageRepository.findById(recentMessage.getAiChatMessageId())).isPresent();
    }

    // ──────────────────── AiPlanExpirationScheduler (dirty checking) ────────────────────

    @Test
    void 만료일이_지난_구독만_스케줄러_실행_후_DB에서_비활성화된다() {
        // given
        AiPlanSubscription expired = aiPlanSubscriptionRepository.save(AiPlanSubscription.createWithPeriod(
                store, AiPlanType.BASIC, LocalDateTime.now().minusMonths(1), LocalDateTime.now().minusDays(1)));
        AiPlanSubscription active = aiPlanSubscriptionRepository.save(AiPlanSubscription.createWithPeriod(
                store, AiPlanType.PRO, LocalDateTime.now(), LocalDateTime.now().plusMonths(1)));

        // when — expire()는 save 호출 없이 dirty checking에만 의존한다
        aiPlanExpirationScheduler.expireSubscriptions();

        // then — 재조회로 커밋 여부 검증
        assertThat(aiPlanSubscriptionRepository.findById(expired.getAiPlanSubscriptionId())
                .orElseThrow().isActive()).isFalse();
        assertThat(aiPlanSubscriptionRepository.findById(active.getAiPlanSubscriptionId())
                .orElseThrow().isActive()).isTrue();
    }

    // ──────────────────── AiScheduledMessageProcessor (LAZY 연관 + 상태 전이) ────────────────────

    private AiGeneratedMessage saveScheduledMessage(LocalDateTime scheduledAt) {
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.NOTICE, null, null, "공지 제목", "공지 내용", AiChannel.APP_PUSH);
        message.edit(null, null);                          // DRAFT → REVIEWED
        message.schedule(scheduledAt, LocalDateTime.now()); // REVIEWED → SCHEDULED
        return aiGeneratedMessageRepository.save(message);
    }

    @Test
    void 발송_가능_판정은_트랜잭션_경계_밖에서_새로_조회한_엔티티_기준으로_동작한다() {
        // given — scheduledAt은 미래(백그라운드 스케줄러 회피), 판정 시점만 그 이후로
        LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);
        AiGeneratedMessage message = saveScheduledMessage(scheduledAt);

        // when & then
        assertThat(aiScheduledMessageProcessor.isDispatchable(
                message.getAiGeneratedMessageId(), scheduledAt.plusMinutes(1))).isTrue();
        assertThat(aiScheduledMessageProcessor.isDispatchable(
                message.getAiGeneratedMessageId(), LocalDateTime.now())).isFalse();
    }

    @Test
    void 발송_확정_시_LAZY_연관_접근을_포함한_SENT_전이와_액션로그가_DB에_반영된다() {
        // given
        AiGeneratedMessage message = saveScheduledMessage(LocalDateTime.now().plusHours(1));
        LocalDateTime sentAt = LocalDateTime.now().plusHours(2);

        // when — markSent 내부에서 message.getStore()/getOwnerAccount() LAZY 접근이 일어난다
        aiScheduledMessageProcessor.markSent(message.getAiGeneratedMessageId(), sentAt);

        // then — 재조회로 상태 전이 커밋 검증
        AiGeneratedMessage persisted = aiGeneratedMessageRepository
                .findById(message.getAiGeneratedMessageId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AiMessageStatus.SENT);
        assertThat(persisted.getSentAt()).isNotNull();
        assertThat(persisted.getScheduledAt()).isNull();
        assertThat(aiActionLogRepository.findAll())
                .anyMatch(log -> log.getActionType() == AiActionType.MESSAGE_SENT);
    }

    @Test
    void 발송_실패_기록은_save_호출_없이도_재시도_카운트와_FAILED_확정이_DB에_반영된다() {
        // given
        AiGeneratedMessage message = saveScheduledMessage(LocalDateTime.now().plusHours(1));
        Long messageId = message.getAiGeneratedMessageId();

        // when — 1회 실패
        aiScheduledMessageProcessor.recordFailure(messageId, 3);

        // then — dirty checking으로 커밋된 카운트를 재조회로 확인
        AiGeneratedMessage afterFirst = aiGeneratedMessageRepository.findById(messageId).orElseThrow();
        assertThat(afterFirst.getRetryCount()).isEqualTo(1);
        assertThat(afterFirst.getStatus()).isEqualTo(AiMessageStatus.SCHEDULED);

        // when — 최대치까지 실패 반복
        aiScheduledMessageProcessor.recordFailure(messageId, 3);
        aiScheduledMessageProcessor.recordFailure(messageId, 3);

        // then
        AiGeneratedMessage afterMax = aiGeneratedMessageRepository.findById(messageId).orElseThrow();
        assertThat(afterMax.getRetryCount()).isEqualTo(3);
        assertThat(afterMax.getStatus()).isEqualTo(AiMessageStatus.FAILED);
    }
}
