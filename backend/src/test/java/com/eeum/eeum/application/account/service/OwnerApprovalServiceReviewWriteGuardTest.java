package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.product.dto.request.RepresentativeMenuCreateRequestDto;
import com.eeum.eeum.application.store.dto.request.SettlementAccountRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessHourUpdateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessInfoRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 심사 부속정보 쓰기의 직렬화 회귀.
 *
 * 승인 전 입력 API는 account/owner_info를 잠그지 않아, 승인이 먼저 커밋돼도 그 전에 시작한
 * 저장이 APPROVED 뒤에 반영됐다. 필터 통과 후의 정지·탈퇴도 걸러지지 않았다.
 */
@ExtendWith(MockitoExtension.class)
class OwnerApprovalServiceReviewWriteGuardTest {

    @InjectMocks OwnerApprovalService ownerApprovalService;
    @Mock AccountWriteGuard accountWriteGuard;
    @Mock AccountWriteTransactions accountWriteTransactions;
    @Mock OwnerBusinessSnapshotReader ownerBusinessSnapshotReader;
    @Mock com.eeum.eeum.application.auth.service.BusinessVerificationService businessVerificationService;
    @Mock AccountRepository accountRepository;
    @Mock OwnerInfoRepository ownerInfoRepository;
    @Mock StoreRepository storeRepository;
    @Mock SettlementAccountRepository settlementAccountRepository;
    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductCategoryRepository productCategoryRepository;
    @Mock StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock OwnerApplicationMapper ownerApplicationMapper;
    @Mock StoreApprovalMapper storeApprovalMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    private static final Long ACCOUNT_ID = 1L;

    private void givenApprovedOwner() {
        Account account = Account.createUser("owner@test.com", "pw", "김사장", "사장닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        OwnerInfo ownerInfo = OwnerInfo.create(account, "1234567890", LocalDate.of(2020, 1, 1));
        ownerInfo.approve();
        when(ownerInfoRepository.findByAccountIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(ownerInfo));
    }

    private void assertAlreadyApproved(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OWNER_ALREADY_APPROVED);
    }

    @Test
    void 승인_뒤에는_어떤_심사_정보도_저장되지_않는다() {
        // given
        givenApprovedOwner();

        // when & then — 네 경로가 모두 같은 가드를 탄다
        assertAlreadyApproved(() ->
                ownerApprovalService.saveSettlementAccount(ACCOUNT_ID, mock(SettlementAccountRequestDto.class)));
        assertAlreadyApproved(() ->
                ownerApprovalService.updateBusinessHours(ACCOUNT_ID, mock(StoreBusinessHourUpdateRequestDto.class)));
        assertAlreadyApproved(() ->
                ownerApprovalService.updateStoreBusinessInfo(ACCOUNT_ID, mock(StoreBusinessInfoRequestDto.class)));
        assertAlreadyApproved(() ->
                ownerApprovalService.saveRepresentativeMenu(ACCOUNT_ID, mock(RepresentativeMenuCreateRequestDto.class)));

        // 상점까지 내려가기 전에 막아야 한다
        verify(storeRepository, never()).findByAccount_AccountId(any());
        verify(settlementAccountRepository, never()).save(any());
    }

    // 잠금 순서가 승인(account → owner_info)과 같아야 승인 커밋과 엇갈리지 않는다
    @Test
    void 계정을_먼저_잠그고_owner_info를_잠근다() {
        givenApprovedOwner();

        assertAlreadyApproved(() ->
                ownerApprovalService.saveSettlementAccount(ACCOUNT_ID, mock(SettlementAccountRequestDto.class)));

        InOrder inOrder = inOrder(accountWriteGuard, ownerInfoRepository);
        inOrder.verify(accountWriteGuard).lockActive(ACCOUNT_ID);
        inOrder.verify(ownerInfoRepository).findByAccountIdWithLock(ACCOUNT_ID);
    }

    // 필터를 통과한 뒤 정지·탈퇴한 계정도 여기서 걸린다
    @Test
    void 정지된_계정은_owner_info를_잠그기_전에_막힌다() {
        when(accountWriteGuard.lockActive(ACCOUNT_ID))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));

        assertThatThrownBy(() ->
                ownerApprovalService.saveSettlementAccount(ACCOUNT_ID, mock(SettlementAccountRequestDto.class)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verify(ownerInfoRepository, never()).findByAccountIdWithLock(any());
    }
}
