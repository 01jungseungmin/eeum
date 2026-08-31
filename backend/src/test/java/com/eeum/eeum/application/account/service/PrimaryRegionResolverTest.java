package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 대표 지역 판정 테스트.
 * 커뮤니티 공개 범위가 이 판정에 달려 있어, 같은 판정이 여러 곳에 복사되면
 * 한쪽만 고쳐져 "목록에는 안 보이는데 다른 경로로는 접근되는" 구멍이 생긴다.
 */
@ExtendWith(MockitoExtension.class)
class PrimaryRegionResolverTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long ACCOUNT_REGION_ID = 100L;

    @Mock private AccountRegionRepository accountRegionRepository;

    @InjectMocks
    private PrimaryRegionResolver primaryRegionResolver;

    @Test
    void 인증된_대표_지역을_반환한다() {
        Region region = region(200L);
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(ACCOUNT_REGION_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(accountRegion(region, true)));

        Region resolved = primaryRegionResolver.resolve(account(ACCOUNT_REGION_ID));

        assertThat(resolved.getRegionId()).isEqualTo(200L);
    }

    @Test
    void 대표_지역을_고르지_않았으면_거절한다() {
        assertThatThrownBy(() -> primaryRegionResolver.resolve(account(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
    }

    @Test
    void 대표로_지정된_활동_지역이_없으면_거절한다() {
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(ACCOUNT_REGION_ID, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> primaryRegionResolver.resolve(account(ACCOUNT_REGION_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
    }

    @Test
    void GPS_인증이_안_된_지역은_거절한다() {
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(ACCOUNT_REGION_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(accountRegion(region(200L), false)));

        assertThatThrownBy(() -> primaryRegionResolver.resolve(account(ACCOUNT_REGION_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_VERIFIED);
    }

    private Account account(Long primaryRegionId) {
        Account account = Account.createUser(
                "user@test.com", "encoded-pw", "사용자", "사용자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        ReflectionTestUtils.setField(account, "primaryRegionId", primaryRegionId);
        return account;
    }

    private Region region(Long regionId) {
        Region region = Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3);
        ReflectionTestUtils.setField(region, "regionId", regionId);
        return region;
    }

    private AccountRegion accountRegion(Region region, boolean verified) {
        AccountRegion accountRegion = AccountRegion.create(
                account(ACCOUNT_REGION_ID), region);
        if (verified) {
            accountRegion.verify();
        }
        return accountRegion;
    }
}
