package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.dto.response.NotificationResponseDto;
import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.entity.Notification;
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
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        when(sseEmitterManager.isConnected(ACCOUNT_ID)).thenReturn(true);

        // when
        NotificationResponseDto result = notificationService.createNotificationWithoutPush(request());

        // then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any());
        verify(sseEmitterManager).sendUnreadCount(eq(ACCOUNT_ID), any(UnreadCountResponseDto.class));
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

    // ===================== 읽음 처리 =====================

    @Test
    void 단건_읽음_처리시_unread_감소_후_SSE로_최신_카운트를_전송한다() {
        // given
        stubRedisValueOps();
        Long notificationId = 100L;
        Notification notification = mock(Notification.class);
        when(notification.isUnread()).thenReturn(true);
        when(notificationRepository.findByNotificationIdAndAccount_AccountId(notificationId, ACCOUNT_ID))
                .thenReturn(Optional.of(notification));
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(2L);
        when(sseEmitterManager.isConnected(ACCOUNT_ID)).thenReturn(true);
        lenient().when(valueOperations.get(anyString())).thenReturn("2");

        // when
        notificationService.markAsRead(ACCOUNT_ID, notificationId);

        // then
        verify(notification).markAsRead();
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("unread:account:" + ACCOUNT_ID)));
        verify(sseEmitterManager).sendUnreadCount(eq(ACCOUNT_ID), any(UnreadCountResponseDto.class));
    }

    @Test
    void 이미_읽은_알림은_카운트_감소나_SSE_전송을_하지_않는다() {
        // given
        Long notificationId = 100L;
        Notification notification = mock(Notification.class);
        when(notification.isUnread()).thenReturn(false);
        when(notificationRepository.findByNotificationIdAndAccount_AccountId(notificationId, ACCOUNT_ID))
                .thenReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(ACCOUNT_ID, notificationId);

        // then
        verify(notification, never()).markAsRead();
        verify(sseEmitterManager, never()).sendUnreadCount(anyLong(), any());
    }

    @Test
    void 전체_읽음_처리시_캐시를_초기화하고_SSE를_전송한다() {
        // given
        stubRedisValueOps();
        when(notificationRepository.markAllAsReadByAccountId(eq(ACCOUNT_ID), any(LocalDateTime.class)))
                .thenReturn(5);
        when(sseEmitterManager.isConnected(ACCOUNT_ID)).thenReturn(true);
        lenient().when(valueOperations.get(anyString())).thenReturn("0");

        // when
        notificationService.markAllAsRead(ACCOUNT_ID);

        // then
        verify(redisTemplate).delete("unread:account:" + ACCOUNT_ID);
        verify(sseEmitterManager).sendUnreadCount(eq(ACCOUNT_ID), any(UnreadCountResponseDto.class));
    }

    @Test
    void 채팅방_읽음시_해당_방의_CHAT_MESSAGE_알림만_읽음_처리하고_캐시를_재계산한다() {
        // given
        stubRedisValueOps();
        Long roomId = 7L;
        when(notificationRepository.markAsReadByAccountAndTypeAndRef(
                eq(ACCOUNT_ID), eq(NotificationType.CHAT_MESSAGE),
                eq(NotificationRefType.CHAT_ROOM), eq(roomId), any(LocalDateTime.class)))
                .thenReturn(3);
        when(notificationRepository.countByAccount_AccountIdAndIsReadFalse(ACCOUNT_ID)).thenReturn(19L);
        when(sseEmitterManager.isConnected(ACCOUNT_ID)).thenReturn(true);
        lenient().when(valueOperations.get(anyString())).thenReturn("19");

        // when
        notificationService.markAsReadByRef(
                ACCOUNT_ID, NotificationType.CHAT_MESSAGE, NotificationRefType.CHAT_ROOM, roomId);

        // then: 대상 조건(계정 + 타입 + 참조)이 정확히 전달되고, DB 기준으로 Redis가 재설정된다
        verify(notificationRepository).markAsReadByAccountAndTypeAndRef(
                eq(ACCOUNT_ID), eq(NotificationType.CHAT_MESSAGE),
                eq(NotificationRefType.CHAT_ROOM), eq(roomId), any(LocalDateTime.class));
        verify(valueOperations).set("unread:account:" + ACCOUNT_ID, "19");
        verify(sseEmitterManager).sendUnreadCount(eq(ACCOUNT_ID), any(UnreadCountResponseDto.class));
    }

    @Test
    void 참조_기준_읽음_처리_대상이_없으면_SSE를_전송하지_않는다() {
        // given
        Long roomId = 7L;
        when(notificationRepository.markAsReadByAccountAndTypeAndRef(
                anyLong(), any(), any(), anyLong(), any(LocalDateTime.class)))
                .thenReturn(0);

        // when
        notificationService.markAsReadByRef(
                ACCOUNT_ID, NotificationType.CHAT_MESSAGE, NotificationRefType.CHAT_ROOM, roomId);

        // then
        verify(sseEmitterManager, never()).sendUnreadCount(anyLong(), any());
    }
}
