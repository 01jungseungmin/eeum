package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.used.service.UsedProductWithdrawalService;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 본인 탈퇴와 관리자 강제 탈퇴가 공유하는 뒷정리 절차 테스트.
 */
@ExtendWith(MockitoExtension.class)
class AccountWithdrawalProcessorTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock private AccountRepository accountRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private OwnerStoreWithdrawalService ownerStoreWithdrawalService;
    @Mock private FavoriteService favoriteService;
    @Mock UsedProductWithdrawalService usedProductWithdrawalService;
    @Mock private UsedProductRepository usedProductRepository;

    @InjectMocks
    private AccountWithdrawalProcessor accountWithdrawalProcessor;

    @Test
    void 탈퇴_처리_후_flush하고_찜을_정리한다() {
        // given — 찜 카운트 감소는 영속성 컨텍스트를 비우는 bulk UPDATE라,
        // 앞 단계 변경을 먼저 flush하지 않으면 탈퇴 상태가 유실된다
        Account account = givenUser();

        // when
        accountWithdrawalProcessor.process(account);

        // then
        InOrder inOrder = inOrder(account, accountRepository, favoriteService);
        inOrder.verify(account).withdraw();
        inOrder.verify(accountRepository).flush();
        inOrder.verify(favoriteService).deleteAllByAccountId(ACCOUNT_ID);
    }

    @Test
    void 사장_계정은_상점을_먼저_비활성화한다() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(ACCOUNT_ID);
        when(owner.getRole()).thenReturn(AccountRole.ROLE_OWNER);
        when(favoriteService.findFavoriteRefIds(ACCOUNT_ID, FavoriteRefType.STORE))
                .thenReturn(List.of());
        when(storeRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when
        accountWithdrawalProcessor.process(owner);

        // then
        InOrder inOrder = inOrder(ownerStoreWithdrawalService, owner);
        inOrder.verify(ownerStoreWithdrawalService).deactivateForWithdrawal(ACCOUNT_ID);
        inOrder.verify(owner).withdraw();
    }

    @Test
    void 예약_중인_중고_거래를_탈퇴_처리보다_먼저_정리한다() {
        // given — 뒤에 두면 seller가 이미 비활성이라 게시글이 조회에서 걸러진다.
        // 정리를 건너뛰면 예약은 RESERVED로 남고 구매자는 볼 수도 없는 글을 통보 없이 기다린다.
        Account account = givenUser();

        // when
        accountWithdrawalProcessor.process(account);

        // then
        InOrder inOrder = inOrder(usedProductWithdrawalService, account);
        inOrder.verify(usedProductWithdrawalService)
                .cancelReservationsForSellerInactivation(ACCOUNT_ID);
        inOrder.verify(account).withdraw();
    }

    @Test
    void 일반_회원은_상점_비활성화를_거치지_않는다() {
        accountWithdrawalProcessor.process(givenUser());

        verify(ownerStoreWithdrawalService, never()).deactivateForWithdrawal(any());
    }

    @Test
    void 건드릴_상점을_ID_오름차순으로_잠근다() {
        // given — 서로의 상점을 찜한 두 사장이 동시에 탈퇴하면 각자 자기 상점을 잡고
        // 상대 상점을 기다리는 순환 교착이 난다. 잠금 순서를 하나로 통일해야 사이클이 없다.
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(ACCOUNT_ID);
        when(owner.getRole()).thenReturn(AccountRole.ROLE_OWNER);
        when(favoriteService.findFavoriteRefIds(ACCOUNT_ID, FavoriteRefType.STORE))
                .thenReturn(List.of(30L, 10L));

        Store ownStore = mock(Store.class);
        when(ownStore.getStoreId()).thenReturn(20L);
        when(storeRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(ownStore));

        // when
        accountWithdrawalProcessor.process(owner);

        // then — 자기 상점(20)과 찜한 상점(30, 10)이 섞여도 오름차순이어야 한다
        ArgumentCaptor<Long> lockedIds = ArgumentCaptor.forClass(Long.class);
        verify(storeRepository, times(3)).findByIdWithPessimisticLock(lockedIds.capture());
        assertThat(lockedIds.getAllValues()).containsExactly(10L, 20L, 30L);
    }

    @Test
    void 건드릴_중고_게시글도_ID_오름차순으로_잠근다() {
        // given — 예약 정리는 내 글을, 찜 정리는 내가 찜한 남의 글을 잠근다.
        // 두 단계로 나눠 잡으면 서로의 글을 찜한 두 판매자가 동시에 탈퇴할 때
        // 각자 자기 글을 잡고 상대 글을 기다리는 순환 대기가 난다.
        Account account = givenUser();
        when(favoriteService.findFavoriteRefIds(ACCOUNT_ID, FavoriteRefType.USED_PRODUCT))
                .thenReturn(List.of(30L, 10L));
        when(usedProductRepository.findReservedProductIdsBySeller(
                ACCOUNT_ID, UsedProductStatus.RESERVED))
                .thenReturn(List.of(20L));

        // when
        accountWithdrawalProcessor.process(account);

        // then — 내 예약 글(20)과 찜한 글(30, 10)이 섞여도 오름차순이어야 한다
        ArgumentCaptor<Long> lockedIds = ArgumentCaptor.forClass(Long.class);
        verify(usedProductRepository, times(3))
                .findByUsedProductIdForUpdate(lockedIds.capture());
        assertThat(lockedIds.getAllValues()).containsExactly(10L, 20L, 30L);
    }

    @Test
    void 같은_게시글이_예약과_찜에_동시에_있어도_한_번만_잠근다() {
        // 중복 잠금은 교착을 만들지는 않지만 불필요한 쿼리다.
        Account account = givenUser();
        when(favoriteService.findFavoriteRefIds(ACCOUNT_ID, FavoriteRefType.USED_PRODUCT))
                .thenReturn(List.of(10L));
        when(usedProductRepository.findReservedProductIdsBySeller(
                ACCOUNT_ID, UsedProductStatus.RESERVED))
                .thenReturn(List.of(10L));

        accountWithdrawalProcessor.process(account);

        verify(usedProductRepository, times(1)).findByUsedProductIdForUpdate(10L);
    }

    private Account givenUser() {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(ACCOUNT_ID);
        when(account.getRole()).thenReturn(AccountRole.ROLE_USER);
        when(favoriteService.findFavoriteRefIds(ACCOUNT_ID, FavoriteRefType.STORE))
                .thenReturn(List.of());
        when(storeRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.empty());
        return account;
    }
}
