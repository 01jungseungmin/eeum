package com.eeum.eeum.security;

import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("ownerApprovalAccessChecker")
@RequiredArgsConstructor
public class OwnerApprovalAccessChecker {

    private final OwnerInfoRepository ownerInfoRepository;

    public boolean canAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            return false;
        }

        Long accountId = userDetails.getAccountId();

        return ownerInfoRepository.findByAccount_AccountId(accountId)
                .map(this::isApprovalAccessible)
                .orElse(false);
    }

    private boolean isApprovalAccessible(OwnerInfo ownerInfo) {
        return ownerInfo.getApprovalStatus() == ApprovalStatus.PENDING
                || ownerInfo.getApprovalStatus() == ApprovalStatus.REJECTED;
    }
}