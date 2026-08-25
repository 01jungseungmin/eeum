package com.eeum.eeum.application.ai.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkAdapter;
import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.eeum.eeum.infrastructure.push.PushMessage;
import com.eeum.eeum.infrastructure.push.PushResult;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 메시지 발송(dispatch) 전체 흐름 통합 테스트 — 실제 MySQL/Redis Testcontainer 환경에서 실행.
 * 외부 채널(FCM/알림톡)만 @MockitoBean으로 대체하고, 나머지(분산 락, claim/record 트랜잭션 분리,
 * 수신 동의·DND·토큰 필터, 알림함 기록, 무효 토큰 정리, 멱등성)는 전부 실제로 동작시킨다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AiMessageDispatchIntegrationTest extends IntegrationTestSupport {



    @MockitoBean
    private PushAdapter pushAdapter;

    @MockitoBean
    private AlimtalkAdapter alimtalkAdapter;

    private final AiMessageDispatchService aiMessageDispatchService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationRepository notificationRepository;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiMessageDeliveryRepository aiMessageDeliveryRepository;

    private Account owner;
    private Store store;
    private Long consentedWithTokenId;
    private Long noConsentId;
    private Long consentedNoTokenId;
    private Long consentedDndId;

    @BeforeEach
    void setUp() {
        owner = accountRepository.save(
                Account.createOwner("dispatch-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        store = storeRepository.save(
                Store.createForOwnerSignup(owner, "발송 테스트 상점", "서울시", "02-0000-0000"));

        consentedWithTokenId = saveFavoriteCustomer(
                "c1@test.com", "010-2222-0001", true, "fcm-token-1", false);
        noConsentId = saveFavoriteCustomer(
                "c2@test.com", "010-2222-0002", false, "fcm-token-2", false);
        consentedNoTokenId = saveFavoriteCustomer(
                "c3@test.com", "010-2222-0003", true, null, false);
        consentedDndId = saveFavoriteCustomer(
                "c4@test.com", "010-2222-0004", true, "fcm-token-4", true);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        aiMessageDeliveryRepository.deleteAll();
        aiGeneratedMessageRepository.deleteAll();
        notificationSettingsRepository.deleteAll();
        favoriteRepository.deleteAll();
        storeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ──────────────────── Helpers ────────────────────

    private Long saveFavoriteCustomer(
            String email, String phone, boolean marketingConsent, String fcmToken, boolean dndAlwaysOn) {
        Account customer = accountRepository.save(
                Account.createUser(email, "encoded_pw", "고객", "닉-" + email, phone));
        if (fcmToken != null) {
            customer.updateFcmToken(fcmToken);
            customer = accountRepository.save(customer);
        }
        NotificationSettings settings = NotificationSettings.createDefault(customer);
        if (marketingConsent) {
            settings.agreeToMarketing();
        }
        if (dndAlwaysOn) {
            settings.updateDnd(true, LocalTime.of(0, 0), LocalTime.of(23, 59, 59));
        }
        notificationSettingsRepository.save(settings);
        favoriteRepository.save(Favorite.create(customer, FavoriteRefType.STORE, store.getStoreId()));
        return customer.getAccountId();
    }

    private Long saveSentMarketingMessage() {
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.EVENT_MARKETING, null, null,
                "여름 이벤트", "시원한 여름 할인 소식!", AiChannel.APP_PUSH);
        message.edit(null, null);              // DRAFT → REVIEWED
        message.send(LocalDateTime.now());     // REVIEWED → SENT (즉시 발송 경로)
        return aiGeneratedMessageRepository.save(message).getAiGeneratedMessageId();
    }

    private Map<Long, AiMessageDelivery> deliveriesByAccountId() {
        return aiMessageDeliveryRepository.findAll().stream()
                .filter(delivery -> delivery.getTargetAccountId() != null)
                .collect(Collectors.toMap(AiMessageDelivery::getTargetAccountId, Function.identity()));
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 발송_시_동의_토큰_DND_상태에_따라_수신자별_발송_결과가_기록된다() {
        // given
        Long messageId = saveSentMarketingMessage();
        when(pushAdapter.send(any(PushMessage.class))).thenReturn(PushResult.success("fcm-msg-1"));

        // when
        aiMessageDispatchService.dispatch(messageId);

        // then — 수신자별 발송 이력을 DB 재조회로 검증
        Map<Long, AiMessageDelivery> deliveries = deliveriesByAccountId();
        assertThat(deliveries.get(consentedWithTokenId).getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(deliveries.get(noConsentId).getStatus()).isEqualTo(AiDeliveryStatus.SKIPPED_NO_CONSENT);
        assertThat(deliveries.get(consentedNoTokenId).getStatus()).isEqualTo(AiDeliveryStatus.SKIPPED_NO_TOKEN);
        assertThat(deliveries.get(consentedDndId).getStatus()).isEqualTo(AiDeliveryStatus.SKIPPED_DND);

        // FCM은 동의+토큰+DND 아님 조건을 전부 만족한 1명에게만 발송된다
        verify(pushAdapter, times(1)).send(any(PushMessage.class));

        // 알림함(Notification)은 동의 필터를 통과한 대상 전원에게 남는다 (토큰 없음/DND 스킵 포함)
        assertThat(marketingNotificationCount(consentedWithTokenId)).isEqualTo(1);
        assertThat(marketingNotificationCount(consentedNoTokenId)).isEqualTo(1);
        assertThat(marketingNotificationCount(consentedDndId)).isEqualTo(1);
        assertThat(marketingNotificationCount(noConsentId)).isZero();
    }

    @Test
    void 같은_메시지를_두_번_발송해도_발송_이력이_있으면_재발송되지_않는다() {
        // given
        Long messageId = saveSentMarketingMessage();
        when(pushAdapter.send(any(PushMessage.class))).thenReturn(PushResult.success("fcm-msg-1"));

        // when
        aiMessageDispatchService.dispatch(messageId);
        long deliveryCountAfterFirst = aiMessageDeliveryRepository.count();
        aiMessageDispatchService.dispatch(messageId);

        // then — 발송 이력 수 불변, FCM 호출도 1회로 유지
        assertThat(aiMessageDeliveryRepository.count()).isEqualTo(deliveryCountAfterFirst);
        verify(pushAdapter, times(1)).send(any(PushMessage.class));
    }

    @Test
    void 무효_토큰_실패_시_FAILED로_기록되고_계정의_FCM_토큰이_정리된다() {
        // given
        Long messageId = saveSentMarketingMessage();
        when(pushAdapter.send(any(PushMessage.class)))
                .thenReturn(PushResult.failure("UNREGISTERED", true));

        // when
        aiMessageDispatchService.dispatch(messageId);

        // then
        assertThat(deliveriesByAccountId().get(consentedWithTokenId).getStatus())
                .isEqualTo(AiDeliveryStatus.FAILED);
        Account account = accountRepository.findById(consentedWithTokenId).orElseThrow();
        assertThat(account.getFcmToken()).isNull();
    }

    @Test
    void 발송_대상이_없으면_NO_TARGET_이력만_남는다() {
        // given — 찜/주문 고객이 전혀 없는 새 상점
        Account otherOwner = accountRepository.save(
                Account.createOwner("dispatch-owner2@test.com", "encoded_pw", "점주2", "010-9999-9999"));
        Store emptyStore = storeRepository.save(
                Store.createForOwnerSignup(otherOwner, "고객 없는 상점", "서울시", "02-9999-9999"));
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                emptyStore, otherOwner, AiMessageType.EVENT_MARKETING, null, null,
                "이벤트", "내용", AiChannel.APP_PUSH);
        message.edit(null, null);
        message.send(LocalDateTime.now());
        Long messageId = aiGeneratedMessageRepository.save(message).getAiGeneratedMessageId();

        // when
        aiMessageDispatchService.dispatch(messageId);

        // then
        List<AiMessageDelivery> deliveries = aiMessageDeliveryRepository.findAll().stream()
                .filter(delivery -> delivery.getMessage().getAiGeneratedMessageId().equals(messageId))
                .toList();
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
    }

    private long marketingNotificationCount(Long accountId) {
        return notificationRepository
                .findAllByAccount_AccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, 10))
                .stream()
                .filter(notification -> notification.getType() == NotificationType.MARKETING_EVENT)
                .count();
    }
}
