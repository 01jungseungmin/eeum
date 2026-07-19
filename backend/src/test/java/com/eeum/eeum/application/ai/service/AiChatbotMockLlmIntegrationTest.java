package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiChatMessageRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiChatResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiChatMessage;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.entity.AiUsageLog;
import com.eeum.eeum.domain.ai.enums.AiChatRole;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiChatMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.ai.repository.AiUsageLogRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 챗봇 전체 흐름 통합 테스트 — ai.provider=mock으로 LLM 라우터가 MockAiClient만 사용하게 해서
 * 외부 API 호출 없이 실제 MySQL/Redis Testcontainer 환경에서 실행.
 *
 * 단위 테스트(Mock)로는 검증 불가한 지점만 다룬다:
 * - LLM 라우터 → MockAiClient → JSON 파싱 → 응답 생성의 실제 빈 배선 (ai.provider 프로퍼티 기반 선택)
 * - 트랜잭션 없는 answer() 안에서 detached store.getAccount() LAZY 프록시를 FK로만 사용해
 *   대화 기록이 저장되는 흐름 (LazyInitializationException 없이 동작해야 함)
 * - 플랜 게이팅(FREE 차단)과 월 사용량 한도(BASIC 30회)가 실제 DB 카운트 + Redis 락으로 동작하는지
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AiChatbotMockLlmIntegrationTest {

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
        registry.add("ai.provider", () -> "mock"); // 외부 LLM 호출 차단 — MockAiClient 단독 체인
    }

    private final AiChatbotService aiChatbotService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final com.eeum.eeum.application.ai.policy.AiPlanPolicy aiPlanPolicy;

    private Account owner;
    private Store store;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        owner = accountRepository.save(
                Account.createOwner("chatbot-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        ownerId = owner.getAccountId();
        store = storeRepository.save(
                Store.createForOwnerSignup(owner, "챗봇 테스트 상점", "서울시", "02-0000-0000"));
    }

    @AfterEach
    void tearDown() {
        aiChatMessageRepository.deleteAll();
        aiUsageLogRepository.deleteAll();
        aiPlanSubscriptionRepository.deleteAll();
        storeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private void subscribeBasic() {
        aiPlanSubscriptionRepository.save(
                AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now()));
    }

    private AiChatMessageRequestDto textRequest(String text) {
        return new AiChatMessageRequestDto(null, text);
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 생성_질문은_Mock_LLM_경유로_응답하고_대화_기록과_사용량이_DB에_남는다() {
        // given
        subscribeBasic();

        // when — "공지" 키워드 → 사용량 차감 + LLM(Mock) 문구 생성 경로
        AiChatResponseDto response = aiChatbotService.answer(ownerId, textRequest("오늘 공지 문구 써줘"));

        // then — 응답: Mock LLM이 생성한 문구가 실려 있고 사용량 카운트 대상
        assertThat(response.isUsageCounted()).isTrue();
        assertThat(response.isOutOfScope()).isFalse();
        assertThat(response.getText()).isNotBlank();

        // DB — USER/ASSISTANT 대화 기록 2건 (detached store.getAccount() 프록시 FK 저장 경로)
        List<AiChatMessage> messages = aiChatMessageRepository.findAll();
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(AiChatMessage::getRole)
                .containsExactlyInAnyOrder(AiChatRole.USER, AiChatRole.ASSISTANT);

        // DB — 월 사용량 1건 기록 (Redis 락 + REQUIRES_NEW 커밋 경로)
        assertThat(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(
                store.getStoreId(), YearMonth.now().toString())).isEqualTo(1);
    }

    @Test
    void 범위_밖_질문은_사용량_차감_없이_안내_응답과_대화_기록만_남는다() {
        // given
        subscribeBasic();

        // when
        AiChatResponseDto response = aiChatbotService.answer(ownerId, textRequest("부가세 신고 어떻게 해?"));

        // then
        assertThat(response.isOutOfScope()).isTrue();
        assertThat(response.isUsageCounted()).isFalse();
        assertThat(aiChatMessageRepository.findAll()).hasSize(2);
        assertThat(aiUsageLogRepository.count()).isZero();
    }

    @Test
    void 구독이_없는_FREE_플랜은_챗봇_접근이_차단되고_대화_기록도_남지_않는다() {
        // given — 구독 미등록 → FREE

        // when & then
        assertThatThrownBy(() -> aiChatbotService.answer(ownerId, textRequest("오늘 공지 문구 써줘")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_REQUIRED);
        assertThat(aiChatMessageRepository.count()).isZero();
    }

    @Test
    void 월_사용량이_BASIC_한도에_도달하면_생성_질문이_거부되고_사용량이_더_늘지_않는다() {
        // given — 이번 달 사용량을 BASIC 한도만큼 채운다 (한도 값은 정책에서 조회해 하드코딩 회피)
        subscribeBasic();
        int basicLimit = aiPlanPolicy.monthlyLimit(AiPlanType.BASIC);
        String yearMonth = YearMonth.now().toString();
        aiUsageLogRepository.saveAll(IntStream.range(0, basicLimit)
                .mapToObj(i -> AiUsageLog.record(store, owner, AiUsageType.CHATBOT_GENERATION, yearMonth))
                .toList());

        // when & then
        assertThatThrownBy(() -> aiChatbotService.answer(ownerId, textRequest("오늘 공지 문구 써줘")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);
        assertThat(aiUsageLogRepository.countByStore_StoreIdAndYearMonth(
                store.getStoreId(), yearMonth)).isEqualTo(basicLimit);

        // 조회성 질문은 한도와 무관하게 계속 동작한다
        AiChatResponseDto response = aiChatbotService.answer(ownerId, textRequest("우리 가게 리뷰 요약해줘"));
        assertThat(response.isUsageCounted()).isFalse();
        assertThat(response.getText()).isNotBlank();
    }
}
