package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.dto.response.NotificationResponseDto;
import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.event.NotificationPushEvent;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    @Mock private UnreadCountService unreadCountService;
    @Mock private SseEmitterManager sseEmitterManager;
    @Mock private UnreadSyncExecutor unreadSyncExecutor;

    private static final Long ACCOUNT_ID = 6L;

    private void stubUnreadCount() {
        UnreadCountResponseDto snapshot = UnreadCountResponseDto.of(1L, Map.of());
        lenient().when(unreadCountService.getUnreadCount(ACCOUNT_ID)).thenReturn(snapshot);
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

    // ===================== 생성 =====================

    @Test
    void createNotification은_동의된_계정에_FCM_푸시_이벤트를_발행한다() {
        // given
        stubUnreadCount();
        stubAccount("valid-token");
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when
        NotificationResponseDto result = notificationService.createNotification(request());

        // then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any());
        verify(unreadSyncExecutor).rebuildAndPush(eq(ACCOUNT_ID), anyLong());
        verify(eventPublisher).publishEvent(any(NotificationPushEvent.class));
    }

    @Test
    void createNotificationWithoutPush는_알림은_생성하되_FCM_이벤트는_발행하지_않는다() {
        // given
        stubUnreadCount();
        stubAccount("valid-token");
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when
        NotificationResponseDto result = notificationService.createNotificationWithoutPush(request());

        // then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any());
        verify(unreadCountService).invalidateSnapshot(ACCOUNT_ID);
        verify(eventPublisher, never()).publishEvent(any(NotificationPushEvent.class));
    }

    @Test
    void createNotificationWithoutPush도_수신_거부_계정이면_생성하지_않는다() {
        // given
        stubAccount("valid-token");
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

    @Test
    void 전체_알림_OFF이면_필수_알림도_생성하지_않는다() {
        // given
        Account account = stubAccount("valid-token");
        NotificationSettings settings = NotificationSettings.createDefault(account);
        settings.updateAllEnabled(false);
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(settings));

        NotificationCreateRequestDto systemNotice = NotificationCreateRequestDto.builder()
                .accountId(ACCOUNT_ID)
                .type(NotificationType.SYSTEM_NOTICE)
                .title("공지")
                .content("내용")
                .build();

        // when
        NotificationResponseDto result = notificationService.createNotification(systemNotice);

        // then
        assertThat(result).isNull();
        verify(notificationRepository, never()).save(any());
        verify(unreadSyncExecutor, never()).rebuildAndPush(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(NotificationPushEvent.class));
    }

    // ===================== 읽음 처리 =====================

    @Test
    void 단건_읽음_처리시_unread_감소_후_SSE로_최신_카운트를_전송한다() {
        // given
        stubUnreadCount();
        Long notificationId = 100L;
        Notification notification = mock(Notification.class);
        when(notification.isUnread()).thenReturn(true);
        when(notificationRepository.findByNotificationIdAndAccount_AccountId(notificationId, ACCOUNT_ID))
                .thenReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(ACCOUNT_ID, notificationId);

        // then
        verify(notification).markAsRead();
        verify(unreadSyncExecutor).rebuildAndPush(eq(ACCOUNT_ID), anyLong());
        verify(unreadCountService).invalidateSnapshot(ACCOUNT_ID);
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
        verify(unreadSyncExecutor, never()).rebuildAndPush(anyLong(), anyLong());
        verify(unreadCountService, never()).invalidateSnapshot(anyLong());
    }

    @Test
    void 전체_읽음_처리시_캐시를_초기화하고_SSE를_전송한다() {
        // given
        stubUnreadCount();
        when(notificationRepository.markAllAsReadByAccountId(eq(ACCOUNT_ID), any(LocalDateTime.class)))
                .thenReturn(5);

        // when
        notificationService.markAllAsRead(ACCOUNT_ID);

        // then
        verify(unreadSyncExecutor).rebuildAndPush(eq(ACCOUNT_ID), anyLong());
        verify(unreadCountService).invalidateSnapshot(ACCOUNT_ID);
    }

    @Test
    void 카테고리_읽음_처리시_해당_타입들을_일괄_처리하고_SSE로_최신_카운트를_전송한다() {
        // given
        stubUnreadCount();
        when(notificationRepository.markAsReadByAccountIdAndTypes(
                eq(ACCOUNT_ID), eq(NotificationCategory.ORDER.getTypes()), any(LocalDateTime.class)))
                .thenReturn(4);

        // when
        notificationService.markCategoryAsRead(ACCOUNT_ID, NotificationCategory.ORDER);

        // then
        verify(notificationRepository).markAsReadByAccountIdAndTypes(
                eq(ACCOUNT_ID), eq(NotificationCategory.ORDER.getTypes()), any(LocalDateTime.class));
        verify(unreadSyncExecutor).rebuildAndPush(eq(ACCOUNT_ID), anyLong());
        verify(unreadCountService).invalidateSnapshot(ACCOUNT_ID);
    }

    @Test
    void 카테고리에_미읽음_알림이_없으면_캐시와_SSE를_갱신하지_않는다() {
        // given
        when(notificationRepository.markAsReadByAccountIdAndTypes(
                eq(ACCOUNT_ID), eq(NotificationCategory.REVIEW.getTypes()), any(LocalDateTime.class)))
                .thenReturn(0);

        // when
        notificationService.markCategoryAsRead(ACCOUNT_ID, NotificationCategory.REVIEW);

        // then
        verify(unreadSyncExecutor, never()).rebuildAndPush(anyLong(), anyLong());
        verify(unreadCountService, never()).invalidateSnapshot(anyLong());
    }

    @Test
    void 커밋_후_unread_동기화가_실패해도_성공한_읽음_처리를_실패로_바꾸지_않는다() {
        // given: 커밋 직후 Redis가 죽어 무효화가 실패하는 상황
        when(notificationRepository.markAsReadByAccountIdAndTypes(
                eq(ACCOUNT_ID), eq(NotificationCategory.ORDER.getTypes()), any(LocalDateTime.class)))
                .thenReturn(2);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(unreadCountService).invalidateSnapshot(ACCOUNT_ID);

        // when & then: 단위 테스트에서는 활성 트랜잭션이 없어 afterCommit 작업이 즉시 실행된다.
        // 이 시점 DB 변경은 이미 커밋됐으므로 예외가 새어 나가면 성공한 요청이 실패로 응답된다.
        assertThatCode(() -> notificationService.markCategoryAsRead(ACCOUNT_ID, NotificationCategory.ORDER))
                .doesNotThrowAnyException();
        // 무효화되지 않은 캐시를 재계산 결과로 덮으면 낡은 값이 다시 살아날 수 있으므로 제출하지 않는다.
        verify(unreadSyncExecutor, never()).rebuildAndPush(anyLong(), anyLong());
    }

    @Test
    void 구독_초기_unread_조회가_실패하면_방금_등록한_emitter를_회수한다() {
        // given: emitter 등록 뒤 Redis/DB 초기 unread 조회가 실패하는 상황
        SseEmitter emitter = mock(SseEmitter.class);
        when(sseEmitterManager.subscribe(ACCOUNT_ID, 2L, "fingerprint")).thenReturn(emitter);
        when(unreadCountService.getUnreadCount(ACCOUNT_ID))
                .thenThrow(new IllegalStateException("redis unavailable"));

        // when & then: 응답으로 반환되지 않은 emitter가 manager에 남아 heartbeat를 받으면 안 된다.
        assertThatThrownBy(() -> notificationService.subscribe(ACCOUNT_ID, 2L, "fingerprint"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
        verify(sseEmitterManager).closeIfCurrent(ACCOUNT_ID, emitter);
    }

    @Test
    void 커밋_후_동기화는_캐시를_먼저_비우고_재계산을_넘긴다() {
        // given
        when(notificationRepository.markAsReadByAccountIdAndTypes(
                eq(ACCOUNT_ID), eq(NotificationCategory.ORDER.getTypes()), any(LocalDateTime.class)))
                .thenReturn(2);

        // when
        notificationService.markCategoryAsRead(ACCOUNT_ID, NotificationCategory.ORDER);

        // then: 순서가 뒤집히면 재계산이 폐기됐을 때 "완성됐지만 낡은" 캐시가 남아
        // 이후 조회가 계속 틀린 값을 돌려준다
        InOrder inOrder = inOrder(unreadCountService, unreadSyncExecutor);
        inOrder.verify(unreadCountService).invalidateSnapshot(ACCOUNT_ID);
        inOrder.verify(unreadSyncExecutor).rebuildAndPush(eq(ACCOUNT_ID), anyLong());
    }

    @Test
    void 커밋_후_동기화는_요청_스레드에서_DB를_다시_읽지_않는다() {
        // given
        when(notificationRepository.markAllAsReadByAccountId(eq(ACCOUNT_ID), any(LocalDateTime.class)))
                .thenReturn(3);

        // when
        notificationService.markAllAsRead(ACCOUNT_ID);

        // then: afterCommit은 커밋을 수행한 요청 스레드에서 돌고 그 시점 바깥 커넥션은
        // 아직 반납 전이다. 여기서 DB를 다시 읽으면 한 요청이 커넥션 2개를 점유한다(R2).
        verify(unreadCountService, never()).refreshFromDb(anyLong());
        verify(unreadCountService, never()).getUnreadCount(anyLong());
        verify(unreadSyncExecutor).rebuildAndPush(eq(ACCOUNT_ID), anyLong());
    }

    @Test
    void 채팅방_읽음시_해당_방의_CHAT_MESSAGE_알림만_읽음_처리하고_캐시를_재계산한다() {
        // given
        stubUnreadCount();
        Long roomId = 7L;
        when(notificationRepository.markAsReadByAccountAndTypeAndRef(
                eq(ACCOUNT_ID), eq(NotificationType.CHAT_MESSAGE),
                eq(NotificationRefType.CHAT_ROOM), eq(roomId), any(LocalDateTime.class)))
                .thenReturn(3);

        // when
        notificationService.markAsReadByRef(
                ACCOUNT_ID, NotificationType.CHAT_MESSAGE, NotificationRefType.CHAT_ROOM, roomId);

        // then: 대상 조건(계정 + 타입 + 참조)이 정확히 전달되고, DB 기준으로 캐시가 재설정된다
        verify(notificationRepository).markAsReadByAccountAndTypeAndRef(
                eq(ACCOUNT_ID), eq(NotificationType.CHAT_MESSAGE),
                eq(NotificationRefType.CHAT_ROOM), eq(roomId), any(LocalDateTime.class));
        verify(unreadSyncExecutor).rebuildAndPush(eq(ACCOUNT_ID), anyLong());
        verify(unreadCountService).invalidateSnapshot(ACCOUNT_ID);
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
        verify(unreadSyncExecutor, never()).rebuildAndPush(anyLong(), anyLong());
        verify(unreadCountService, never()).invalidateSnapshot(anyLong());
    }
}
