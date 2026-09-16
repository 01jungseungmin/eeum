package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import org.springframework.stereotype.Component;

/**
 * 관리자 제재(경고·정지·강제 탈퇴)의 대상 자격 판정.
 *
 * 제재 경로가 관리자 직접 API와 신고 처리 둘이라, 판정을 각자 적어두면 한쪽만 고쳐져
 * "신고로는 못 건드리는데 관리자 API로는 되는" 구멍이 생긴다(실제로 그랬다).
 * 조치마다 허용 상태가 달라 메서드를 나눈다 — 하나로 합쳐 isSuspended를 전 경로에 걸면
 * 정지 계정의 강제 탈퇴가 막혀 회귀한다. 정지 후 탈퇴는 정상 흐름이다.
 * 자기 제재는 따로 막지 않는다 — ROLE_ADMIN 전용이라 관리자 차단이 이미 포함한다.
 */
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
