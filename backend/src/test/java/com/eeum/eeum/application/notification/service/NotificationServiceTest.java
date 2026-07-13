package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.dto.response.NotificationResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.event.NotificationPushEvent;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSettingsRepository settingsRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SseEmitterManager sseEmitterManager;
    @Mock private ValueOperations<String, String> valueOperations;

    private static final Long ACCOUNT_ID = 6L;

    private void stubRedisValueOps() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
    }

    private Account stubAccount(String fcmToken) {
        Account account = mock(Account.class);
        lenient().when(account.getAccountId()).thenReturn(ACCOUNT_ID);
        lenient().when(account.getFcmToken()).thenReturn(fcmToken);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        return account;
    }

    private NotificationCreateRequestDto request() {
        return NotificationCreateRequestDto.builder()
                .accountId(ACCOUNT_ID)
                .type(NotificationType.MARKETING_EVENT)
                .title("제목")
                .content("내용")
                .refType(NotificationRefType.STORE)
                .refId(1L)
                .linkUrl("/stores/1")
                .build();
    }

    @Test
    void createNotification은_동의된_계정에_FCM_푸시_이벤트를_발행한다() {
        // given
        stubRedisValueOps();
        stubAccount("valid-token");
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when
        NotificationResponseDto result = notificationService.createNotification(request());

        // then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any());
        verify(eventPublisher).publishEvent(any(NotificationPushEvent.class));
    }

    @Test
    void createNotificationWithoutPush는_알림은_생성하되_FCM_이벤트는_발행하지_않는다() {
        // given
        stubRedisValueOps();
        stubAccount("valid-token");
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when
        NotificationResponseDto result = notificationService.createNotificationWithoutPush(request());

        // then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any());
        verify(sseEmitterManager).sendUnreadCount(any(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(NotificationPushEvent.class));
    }

    @Test
    void createNotificationWithoutPush도_수신_거부_계정이면_생성하지_않는다() {
        // given
        Account account = stubAccount("valid-token");
        NotificationSettings settings = mock(NotificationSettings.class);
        when(settings.isAllowed(NotificationType.MARKETING_EVENT)).thenReturn(false);
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(settings));

        // when
        NotificationResponseDto result = notificationService.createNotificationWithoutPush(request());

        // then
        assertThat(result).isNull();
        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(NotificationPushEvent.class));
    }
}
