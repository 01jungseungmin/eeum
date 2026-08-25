package com.eeum.eeum.application.notification.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.notification.dto.request.NotificationSettingsUpdateRequestDto;
import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfDockerAvailable
@RequiredArgsConstructor
class NotificationConcurrencyIntegrationTest extends IntegrationTestSupport {



    private final NotificationSettingsService settingsService;
    private final UnreadCountService unreadCountService;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(Account.createUser(
                "notification-lock@test.com", "encoded-password", "알림락",
                "notification_lock", "010-9900-0001"));
        settingsService.getMySettings(account.getAccountId());
    }

    @AfterEach
    void tearDown() {
        Long accountId = account != null ? account.getAccountId() : null;
        notificationRepository.deleteAll();
        settingsRepository.deleteAll();
        if (accountId != null) {
            redisTemplate.delete("unread:account:" + accountId);
            redisTemplate.delete("unread:category:" + accountId);
        }
    }

    @Test
    void 서로_다른_알림_설정_PATCH가_동시에_실행돼도_두_변경이_모두_보존된다() throws Exception {
        Long accountId = account.getAccountId();
        NotificationSettingsUpdateRequestDto allOff = new NotificationSettingsUpdateRequestDto();
        ReflectionTestUtils.setField(allOff, "allEnabled", false);
        NotificationSettingsUpdateRequestDto chatOff = new NotificationSettingsUpdateRequestDto();
        ReflectionTestUtils.setField(chatOff, "chatEnabled", false);

        ConcurrentLinkedQueue<Throwable> failures = runConcurrently(List.of(
                () -> settingsService.updateSettings(accountId, allOff),
                () -> settingsService.updateSettings(accountId, chatOff)
        ));

        assertThat(failures).isEmpty();
        var persisted = settingsRepository.findByAccount_AccountId(accountId).orElseThrow();
        assertThat(persisted.isAllEnabled()).isFalse();
        assertThat(persisted.isChatEnabled()).isFalse();
    }

    @Test
    void 알림_설정이_없는_계정을_동시에_조회해도_기본_설정은_하나만_생성된다() throws Exception {
        Long accountId = account.getAccountId();
        settingsRepository.deleteAll();

        List<Runnable> requests = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            requests.add(() -> settingsService.getMySettings(accountId));
        }

        ConcurrentLinkedQueue<Throwable> failures = runConcurrently(requests);

        assertThat(failures).isEmpty();
        assertThat(settingsRepository.count()).isEqualTo(1L);
        assertThat(settingsRepository.findByAccount_AccountId(accountId)).isPresent();
    }

    @Test
    void unread_동기화_콜백이_동시에_실행돼도_Redis는_DB_스냅샷과_같다() throws Exception {
        Long accountId = account.getAccountId();
        notificationRepository.saveAndFlush(Notification.create(
                account, NotificationType.NEW_ORDER, "새 주문", "주문이 접수되었습니다.",
                NotificationRefType.STORE, 1L, "/owner/orders"));

        redisTemplate.opsForValue().set("unread:account:" + accountId, "99");
        redisTemplate.delete("unread:category:" + accountId);

        List<Runnable> callbacks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            callbacks.add(switch (i % 4) {
                case 0 -> () -> unreadCountService.increment(accountId);
                case 1 -> () -> unreadCountService.decrement(accountId);
                case 2 -> () -> unreadCountService.clear(accountId);
                default -> () -> unreadCountService.refreshFromDb(accountId);
            });
        }

        ConcurrentLinkedQueue<Throwable> failures = runConcurrently(callbacks);
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(accountId);

        assertThat(failures).isEmpty();
        assertThat(result.getUnreadCount()).isEqualTo(1L);
        assertThat(result.getByCategory().get(NotificationCategory.ORDER)).isEqualTo(1L);
        assertThat(redisTemplate.opsForValue().get("unread:account:" + accountId)).isEqualTo("1");
        assertThat(redisTemplate.opsForHash()
                .get("unread:category:" + accountId, NotificationCategory.ORDER.name()))
                .isEqualTo("1");
    }

    private ConcurrentLinkedQueue<Throwable> runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (Runnable task : tasks) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (Throwable throwable) {
                    failures.add(throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        return failures;
    }
}
