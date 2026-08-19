package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.store.service.StorePhysicalDeleteService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 후 30일 경과 계정의 물리 삭제 테스트.
 * favorite.account_id는 NOT NULL FK라 남아 있으면 계정 삭제가 제약 위반으로 실패한다.
 */
@ExtendWith(MockitoExtension.class)
class AccountCleanupServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock private AccountRepository accountRepository;
    @Mock private AccountRegionRepository accountRegionRepository;
    @Mock private OwnerInfoRepository ownerInfoRepository;
    @Mock private StorePhysicalDeleteService storePhysicalDeleteService;
    @Mock private FavoriteService favoriteService;

    @InjectMocks
    private AccountCleanupService accountCleanupService;

    @Test
    void 계정을_물리_삭제하기_전에_찜을_정리한다() {
        // given
        Account account = account();
        when(accountRepository.findWithdrawnAccountsBefore(any(), any()))
                .thenReturn(List.of(account));

        // when
        accountCleanupService.deleteWithdrawnAccountsAfter30Days();

        // then: 찜이 남은 채 계정을 지우면 FK 제약 위반으로 스케줄러가 실패한다
        InOrder inOrder = inOrder(favoriteService, accountRepository);
        inOrder.verify(favoriteService).deleteAllByAccountId(ACCOUNT_ID);
        inOrder.verify(accountRepository).delete(account);
    }

    @Test
    void 삭제_대상이_없으면_하위_데이터_정리를_시도하지_않는다() {
        when(accountRepository.findWithdrawnAccountsBefore(any(), any()))
                .thenReturn(List.of());

        accountCleanupService.deleteWithdrawnAccountsAfter30Days();

        verifyNoInteractions(favoriteService, storePhysicalDeleteService, accountRegionRepository);
        verify(accountRepository, never()).delete(any());
    }

    private Account account() {
        Account account = Account.createUser(
                "user@test.com", "encoded-pw", "사용자", "사용자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        ReflectionTestUtils.setField(account, "status", AccountStatus.WITHDRAWN);
        ReflectionTestUtils.setField(account, "deletedAt", LocalDateTime.now().minusDays(31));
        return account;
    }
}
