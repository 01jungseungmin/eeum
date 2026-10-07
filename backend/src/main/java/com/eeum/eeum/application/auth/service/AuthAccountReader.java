package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.AccountAuthState;
import java.util.List;
import java.util.Optional;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthAccountReader {
    private final AccountRepository accountRepository;

    public Account byId(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    public Account byEmail(String email) {
        return accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    public boolean emailExists(String email) {
        return accountRepository.existsByEmail(email);
    }

    public Optional<AccountAuthState> authState(Long id) {
        return accountRepository.findAuthStates(List.of(id)).stream().findFirst();
    }
}
