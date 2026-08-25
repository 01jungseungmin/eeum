package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 선택해 둔 동네(대표 지역)를 확정한다.
 *
 * <p>커뮤니티는 대표 지역 단위로 공개된다. 같은 판정을 여러 곳에 복사해 두면 한쪽만 고쳐져
 * "목록에는 안 보이는데 다른 경로로는 접근되는" 구멍이 생긴다. 판정은 여기 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class PrimaryRegionResolver {

    private final AccountRegionRepository accountRegionRepository;

    @Transactional(readOnly = true)
    public Region resolve(Account account) {
        Long primaryAccountRegionId = account.getPrimaryRegionId();

        if (primaryAccountRegionId == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
        }

        AccountRegion accountRegion = accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(
                        primaryAccountRegionId, account.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND));

        if (!accountRegion.isVerified()) {
            throw new BusinessException(ErrorCode.REGION_NOT_VERIFIED);
        }

        return accountRegion.getRegion();
    }
}
