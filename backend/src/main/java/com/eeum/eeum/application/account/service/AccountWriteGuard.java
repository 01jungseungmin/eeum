package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사용자 쓰기 경로의 첫 단계 — 요청자 계정 행을 잠그고 사용 가능 상태를 확인한다.
 *
 * <p>잠그지 않으면 탈퇴 트랜잭션과 겹쳐 두 가지가 생긴다.
 * 하나는 탈퇴 정리(찜 삭제·카운트 감소)가 끝난 뒤 같은 계정의 쓰기가 커밋돼 데이터가 되살아나는 것,
 * 다른 하나는 정리와 쓰기가 같은 카운터를 동시에 건드려 값이 어긋나는 것이다.
 *
 * <p>잠금 순서는 <b>account → store/used_product → favorite/image</b>로 고정한다.
 * 한 경로라도 순서를 뒤집으면 교착이 난다.
 *
 * <p>상태 판정은 {@link Account#assertWritable()}에 있다 — 탈퇴·정지·가입 미완료를 구분해 던진다.
 */
@Component
@RequiredArgsConstructor
public class AccountWriteGuard {

    private final AccountRepository accountRepository;

    public Account lockActive(Long accountId) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        account.assertWritable();
        return account;
    }
}
