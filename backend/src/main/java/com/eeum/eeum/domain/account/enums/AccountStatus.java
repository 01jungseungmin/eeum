package com.eeum.eeum.domain.account.enums;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;

public enum AccountStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    WITHDRAWN;

    /**
     * 쓰기 경로 공통 가드 — 상태별로 구분해서 던진다.
     *
     * <p>판정을 엔티티가 아니라 상태 enum에 둔다. WebSocket 인증처럼 Account 엔티티를 로딩하지 않고
     * 상태 컬럼만 projection으로 읽는 경로가 있는데, 그쪽이 자기만의 분기를 따로 쓰면
     * 한쪽에만 상태가 추가돼 정지 계정이 통과하는 구멍이 생긴다.
     *
     * <p>{@code !isActive()}를 한 덩어리로 묶지 않는다 — 가입 미완료(PENDING) 계정까지
     * "정지된 계정"으로 응답한다.
     */
    public void assertWritable() {
        switch (this) {
            case ACTIVE -> { }
            case WITHDRAWN -> throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
            case SUSPENDED -> throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            case PENDING -> throw new BusinessException(ErrorCode.ACCOUNT_SIGNUP_INCOMPLETE);
        }
    }
}
