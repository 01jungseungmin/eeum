package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import org.springframework.stereotype.Component;

/** 관리자 제재 경로의 대상 자격을 한 곳에서 판정한다. 정지 계정의 강제 탈퇴는 허용한다. */
@Component
public class AccountSanctionPolicy {

    /** 경고 — 이미 정지된 계정에도 이력은 남길 수 있다. */
    public void validateWarnable(Account target) {
        validateSanctionable(target);
    }

    /** 정지 — 중복 정지는 제재 이력과 토큰 정리 이벤트를 한 번 더 만든다. */
    public void validateSuspendable(Account target) {
        validateSanctionable(target);

        if (target.isSuspended()) {
            throw new ConflictException(ErrorCode.ACCOUNT_ALREADY_SUSPENDED);
        }
    }

    /** 강제 탈퇴 — 정지 계정도 대상이다. 정지 → 탈퇴가 정상 순서다. */
    public void validateForceWithdrawable(Account target) {
        validateSanctionable(target);
    }

    private void validateSanctionable(Account target) {
        // 관리자끼리, 그리고 자기 자신에게 제재를 걸 수 있으면 계정 하나로 운영 권한 전체를 잠글 수 있다.
        if (target.isAdmin()) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_ADMIN_SANCTION_NOT_ALLOWED);
        }

        if (target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }
}
