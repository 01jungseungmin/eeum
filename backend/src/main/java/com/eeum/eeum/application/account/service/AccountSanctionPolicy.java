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
 * <p>제재 경로가 둘이다 — 관리자 직접 API({@code AdminAccountService})와 신고 처리
 * ({@code ReportedAccountActionService}). 판정을 각자 적어두면 한쪽만 고쳐져
 * "신고로는 못 건드리는데 관리자 API로는 되는" 구멍이 생긴다. 실제로 관리자 대상 차단이
 * 신고 경로에만 있었다.
 *
 * <p>조치마다 허용 상태가 다르므로 메서드를 나눈다. 하나로 합쳐 모든 경로에
 * {@code isSuspended} 검사를 걸면 <b>정지 계정의 강제 탈퇴</b>가 막혀 회귀한다 —
 * 정지 후 탈퇴는 정상 흐름이다.
 *
 * <p>자기 자신 제재는 별도 검사를 두지 않는다. 이 경로는 모두 {@code ROLE_ADMIN} 전용이라
 * actor == target이면 target이 관리자이고, 관리자 차단이 자기 제재를 포함한다.
 * actor ID를 6개 신고 executor까지 내려보내는 비용만큼의 이득이 없다.
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
