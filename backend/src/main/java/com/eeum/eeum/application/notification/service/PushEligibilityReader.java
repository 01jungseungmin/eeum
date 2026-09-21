package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushEligibilityReader {
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public boolean canSend(Long accountId, String expectedToken) {
        if (accountId == null || expectedToken == null || expectedToken.isBlank()) return false;
        return accountRepository.findById(accountId)
                .filter(account -> account.isActive() && expectedToken.equals(account.getFcmToken()))
                .isPresent();
    }
}
