package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AccountRepositoryCustom {

    Page<Account> searchAccounts(AccountStatus status, AccountRole role, String keyword, Pageable pageable);

    /**
     * 기간 내 가입 계정의 가입 시각 목록. 일자별 집계는 호출부에서 한다.
     *
     * @param ownerApplicant true면 사장으로 가입한 계정(사업자 정보 보유), false면 일반 가입 계정.
     *                       사장 가입자는 승인 전까지 ROLE_USER라 역할로는 구분할 수 없다
     * @param from 가입 시각 하한(포함)
     * @param to 가입 시각 상한(미포함)
     */
    List<LocalDateTime> findSignupTimes(
            boolean ownerApplicant,
            Collection<AccountRole> roles,
            Collection<AccountStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    );
}
