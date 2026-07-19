package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiLocalMatchConditionRequestDto;
import com.eeum.eeum.application.ai.scheduler.AiScheduledMessageProcessor;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 매니저 @Version 낙관적 락 충돌 통합 테스트 — 실제 MySQL Testcontainer 환경에서 실행.
 *
 * Redis 분산 락 해제 ~ 트랜잭션 커밋 사이의 틈에서 생길 수 있는 stale write를
 * 중첩 트랜잭션(REQUIRES_NEW)으로 결정적으로 재현한다:
 * 바깥 TX가 엔티티를 읽은 뒤, 안쪽 TX(다른 요청 역할)가 먼저 커밋해 version을 올리면
 * 바깥 TX의 커밋은 OptimisticLockingFailureException으로 거부되어야 한다.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AiOptimisticLockIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("eeum")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private final AiExposureCommandExecutor aiExposureCommandExecutor;
    private final AiScheduledMessageProcessor aiScheduledMessageProcessor;
    private final PlatformTransactionManager transactionManager;

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final AiExposureStatusRepository aiExposureStatusRepository;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiActionLogRepository aiActionLogRepository;

    private Account owner;
    private Store store;

    @BeforeEach
    void setUp() {
        owner = accountRepository.save(
                Account.createOwner("lock-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        store = storeRepository.save(
                Store.createForOwnerSignup(owner, "낙관적 락 상점", "서울시", "02-0000-0000"));
    }

    @AfterEach
    void tearDown() {
        aiActionLogRepository.deleteAll();
        aiExposureStatusRepository.deleteAll();
        aiGeneratedMessageRepository.deleteAll();
        storeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Test
    void 노출_조건_동시_변경_시_먼저_커밋한_쪽이_이기고_늦은_stale_write는_거부된다() {
        // given — 커밋된 초기 노출 상태 (version 0)
        Long statusId = aiExposureStatusRepository.save(AiExposureStatus.init(store))
                .getAiExposureStatusId();

        // when — 바깥 TX가 stale 엔티티를 들고 있는 사이, 다른 요청(안쪽 TX)이 먼저 커밋
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> outer.executeWithoutResult(txStatus -> {
            AiExposureStatus stale = aiExposureStatusRepository.findById(statusId).orElseThrow();

            requiresNew().executeWithoutResult(inner -> aiExposureCommandExecutor.updateConditionsInTx(
                    store, new AiLocalMatchConditionRequestDto(3.0, "한식", null)));

            stale.updateConditions(1.0, "카페", null, 0); // stale 상태 기반 변경
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // then — 먼저 커밋한 값이 보존된다
        AiExposureStatus persisted = aiExposureStatusRepository.findById(statusId).orElseThrow();
        assertThat(persisted.getRadiusKm()).isEqualTo(3.0);
        assertThat(persisted.getInterest()).isEqualTo("한식");
    }

    @Test
    void 발송_확정과_취소가_경합하면_늦게_커밋한_취소가_거부되고_SENT가_보존된다() {
        // given — SCHEDULED 메시지 (엔티티 주석의 "Redis 락 해제~커밋 사이 틈" 시나리오)
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.NOTICE, null, null, "제목", "내용", AiChannel.APP_PUSH);
        message.edit(null, null);
        message.schedule(LocalDateTime.now().plusHours(1), LocalDateTime.now());
        Long messageId = aiGeneratedMessageRepository.save(message).getAiGeneratedMessageId();

        // when — 취소 요청(바깥 TX)이 stale SCHEDULED 상태를 읽은 사이, 스케줄러(안쪽 TX)가 SENT로 먼저 커밋
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> outer.executeWithoutResult(txStatus -> {
            AiGeneratedMessage stale = aiGeneratedMessageRepository.findById(messageId).orElseThrow();

            requiresNew().executeWithoutResult(inner ->
                    aiScheduledMessageProcessor.markSent(messageId, LocalDateTime.now()));

            stale.cancel(); // 메모리상 SCHEDULED이므로 통과하지만 커밋 시 version 충돌
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // then — 발송 확정(SENT)이 보존되고 취소는 유실되지 않고 거부된다
        AiGeneratedMessage persisted = aiGeneratedMessageRepository.findById(messageId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AiMessageStatus.SENT);
    }
}
