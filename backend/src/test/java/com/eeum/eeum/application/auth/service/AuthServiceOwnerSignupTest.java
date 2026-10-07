package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.application.auth.dto.request.OwnerSignupRequestDto;
import com.eeum.eeum.application.account.service.AccountWriteTransactions;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceOwnerSignupTest {
    @InjectMocks AuthService auth;
    @Mock BusinessVerificationService businessVerificationService;
    @Mock AccountWriteTransactions accountWriteTransactions;

    @Test
    void 사업자_검증_실패시_가입_트랜잭션을_시작하지_않는다() {
        OwnerSignupRequestDto request = mock(OwnerSignupRequestDto.class);
        when(request.getBusinessNumber()).thenReturn("123-45-67890");
        when(request.getOpeningDate()).thenReturn("20200101");
        when(request.getName()).thenReturn("대표자");
        when(businessVerificationService.verifyBusiness("1234567890", "대표자", "2020-01-01"))
                .thenReturn(false);
        assertThatThrownBy(() -> auth.ownerSignup(request)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.BUSINESS_VERIFY_FAILED);
        verifyNoInteractions(accountWriteTransactions);
    }
}
