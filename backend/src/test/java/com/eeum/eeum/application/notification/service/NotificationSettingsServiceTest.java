package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.request.NotificationSettingsUpdateRequestDto;
import com.eeum.eeum.application.notification.dto.response.NotificationSettingsResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @InjectMocks
    private NotificationSettingsService settingsService;

    @Mock private NotificationSettingsRepository settingsRepository;
    @Mock private AccountRepository accountRepository;

    private static final Long ACCOUNT_ID = 6L;

    @Test
    void 전체_알림_설정을_OFF로_변경하면_응답과_수신_정책에_반영된다() {
        // given
        Account account = mock(Account.class);
        NotificationSettings settings = NotificationSettings.createDefault(account);
        NotificationSettingsUpdateRequestDto request = new NotificationSettingsUpdateRequestDto();
        ReflectionTestUtils.setField(request, "allEnabled", false);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(settings));

        // when
        NotificationSettingsResponseDto result = settingsService.updateSettings(ACCOUNT_ID, request);

        // then
        assertThat(result.isAllEnabled()).isFalse();
        assertThat(settings.isAllowed(NotificationType.SYSTEM_NOTICE)).isFalse();
        assertThat(settings.isAllowed(NotificationType.CHAT_MESSAGE)).isFalse();
        verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
    }

    @Test
    void 기본_알림_설정은_전체_ON이다() {
        // given
        Account account = mock(Account.class);
        NotificationSettings settings = NotificationSettings.createDefault(account);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(settingsRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(settings));

        // when
        NotificationSettingsResponseDto result = settingsService.getMySettings(ACCOUNT_ID);

        // then
        assertThat(result.isAllEnabled()).isTrue();
        var inOrder = inOrder(accountRepository, settingsRepository);
        inOrder.verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
        inOrder.verify(settingsRepository).findByAccount_AccountId(ACCOUNT_ID);
    }
}
