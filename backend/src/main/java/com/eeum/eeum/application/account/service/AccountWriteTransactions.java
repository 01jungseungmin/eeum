package com.eeum.eeum.application.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountWriteTransactions {
    // 외부 검증을 끝낸 뒤 DB 상태 재검사와 저장만 별도 프록시 트랜잭션에서 실행한다.
    @Transactional
    public void run(Runnable action) {
        action.run();
    }
}
