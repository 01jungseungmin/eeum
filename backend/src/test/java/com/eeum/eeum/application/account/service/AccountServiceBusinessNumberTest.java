package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.OwnerInfoRequestDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceBusinessNumberTest {

    @InjectMocks AccountService accountService;

    @Mock AccountRepository accountRepository;
    @Mock OwnerInfoRepository ownerInfoRepository;
    @Mock AccountRegionRepository accountRegionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokenService;
    @Mock OwnerStoreWithdrawalService ownerStoreWithdrawalService;
    @Mock AccountMapper accountMapper;
    @Mock OwnerApplicationMapper ownerApplicationMapper;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    // ─────────────────── updateOwnerInfo - 사업자번호 정규화 ───────────────────

    @Test
    void updateOwnerInfo_사업자번호_하이픈_포함_시_정규화하여_저장() {
        // given
        Long accountId = 1L;

        OwnerInfoRequestDto request = mock(OwnerInfoRequestDto.class);
        when(request.getBusinessNumber()).thenReturn("123-45-67890");

        Account account = mock(Account.class);
        // 잠금 순서 account → owner_info — 사장 승인(approveOwner)과 같은 순서여야 한다.
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfo.getBusinessNumber()).thenReturn("9999999999"); // 기존 값과 다름
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(ownerInfoRepository.existsByBusinessNumber("1234567890")).thenReturn(false);

        // when
        accountService.updateOwnerInfo(accountId, request);

        // then: 정규화된 사업자번호("1234567890")로 updateInfo 호출
        verify(ownerInfo).updateInfo("1234567890");
    }

    @Test
    void updateOwnerInfo_정규화된_사업자번호가_기존값과_같으면_중복검사_미호출() {
        // given
        Long accountId = 1L;

        OwnerInfoRequestDto request = mock(OwnerInfoRequestDto.class);
        when(request.getBusinessNumber()).thenReturn("1234567890");

        Account account = mock(Account.class);
        // 잠금 순서 account → owner_info — 사장 승인(approveOwner)과 같은 순서여야 한다.
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfo.getBusinessNumber()).thenReturn("1234567890"); // 기존값과 동일
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));

        // when
        accountService.updateOwnerInfo(accountId, request);

        // then: 동일한 값이므로 중복검사 미호출
        verify(ownerInfoRepository, never()).existsByBusinessNumber(anyString());
        verify(ownerInfo).updateInfo("1234567890");
    }

    @Test
    void updateOwnerInfo_정규화된_사업자번호가_기존값과_다르고_이미_존재하면_예외() {
        // given
        Long accountId = 1L;

        OwnerInfoRequestDto request = mock(OwnerInfoRequestDto.class);
        when(request.getBusinessNumber()).thenReturn("123-45-67890"); // 정규화 후 "1234567890"

        Account account = mock(Account.class);
        // 잠금 순서 account → owner_info — 사장 승인(approveOwner)과 같은 순서여야 한다.
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfo.getBusinessNumber()).thenReturn("9999999999"); // 기존값과 다름
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));
        when(ownerInfoRepository.existsByBusinessNumber("1234567890")).thenReturn(true); // 이미 존재

        // when & then
        assertThatThrownBy(() -> accountService.updateOwnerInfo(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER);

        verify(ownerInfo, never()).updateInfo(anyString());
    }

    @Test
    void updateOwnerInfo_사업자번호_null이면_정규화_결과도_null_중복검사_미호출() {
        // given
        Long accountId = 1L;

        OwnerInfoRequestDto request = mock(OwnerInfoRequestDto.class);
        when(request.getBusinessNumber()).thenReturn(null); // null 입력

        Account account = mock(Account.class);
        // 잠금 순서 account → owner_info — 사장 승인(approveOwner)과 같은 순서여야 한다.
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfoRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(ownerInfo));

        // when
        accountService.updateOwnerInfo(accountId, request);

        // then: null이면 중복검사 미호출, updateInfo(null) 호출
        verify(ownerInfoRepository, never()).existsByBusinessNumber(anyString());
        verify(ownerInfo).updateInfo(null);
    }
}
