package com.eeum.eeum.domain.settlement.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklySettlementTest {

    @Test
    void 만료된_claim은_새_작업자가_재획득할_수_있고_이전_작업자는_완료할_수_없다() {
        // given
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        settlement.claim(admin(1L), "worker-a", now.plusMinutes(1), now, now);
        settlement.claim(admin(2L), "worker-b", now.plusMinutes(10), now.plusMinutes(2), now.plusMinutes(2));

        // when / then
        assertThatThrownBy(() -> settlement.completeManually(
                admin(1L), "worker-a", "manual-transfer-1", now.plusMinutes(3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_CLAIM_MISMATCH);

        assertThat(settlement.getStatus()).isEqualTo(WeeklySettlementStatus.PAYOUT_IN_PROGRESS);
        assertThat(settlement.getClaimToken()).isEqualTo("worker-b");
    }

    @Test
    void 현재_claim을_가진_작업자만_수동_지급을_완료할_수_있다() {
        // given
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        Account admin = admin(1L);
        settlement.claim(admin, "worker-a", now.plusMinutes(10), now, now);

        // when
        settlement.completeManually(admin, "worker-a", "manual-transfer-1", now.plusMinutes(1));

        // then
        assertThat(settlement.getStatus()).isEqualTo(WeeklySettlementStatus.COMPLETED);
        assertThat(settlement.getClaimToken()).isNull();
    }

    @Test
    void 만료된_claim은_실패를_수동검토로_전이할_수_없다() {
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        settlement.claim(admin(1L), "worker-a", now.plusMinutes(1), now, now);
        settlement.markFailed("worker-a", "PAYOUT_FAILED", "실패", now.plusSeconds(30));

        assertThatThrownBy(() -> settlement.requireManualReview(
                "worker-a", "PAYOUT_FAILED", "실패", now.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_CLAIM_MISMATCH);
    }

    @Test
    void 임대가_만료되면_다른_관리자가_지급을_다시_가져갈_수_있다() {
        // given — 앞선 작업자가 지급을 잡아둔 채 사라졌다
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        settlement.claim(admin(1L), "worker-a", now.plusMinutes(10), now, now);

        // when — 임대 시각이 지난 뒤 다른 관리자가 claim 한다
        LocalDateTime afterExpiry = now.plusMinutes(11);
        settlement.claim(admin(2L), "worker-b", afterExpiry.plusMinutes(10), afterExpiry, afterExpiry);

        // then
        assertThat(settlement.getStatus()).isEqualTo(WeeklySettlementStatus.PAYOUT_IN_PROGRESS);
        assertThat(settlement.getClaimedBy().getAccountId()).isEqualTo(2L);
    }

    @Test
    void 임대가_만료된_작업자의_지급_결과는_반영하지_않는다() {
        // given
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        Account worker = admin(1L);
        settlement.claim(worker, "worker-a", now.plusMinutes(10), now, now);

        // when / then — 임대가 끝난 뒤 돌아온 결과는 버린다
        assertThatThrownBy(() -> settlement.completeManually(
                worker, "worker-a", "manual-transfer-1", now.plusMinutes(11)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_CLAIM_MISMATCH);

        assertThat(settlement.getStatus()).isEqualTo(WeeklySettlementStatus.PAYOUT_IN_PROGRESS);
    }

    @Test
    void claim한_관리자가_아니면_지급을_완료할_수_없다() {
        // given
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        settlement.claim(admin(1L), "worker-a", now.plusMinutes(10), now, now);

        // when / then — 토큰을 알아도 잡은 사람이 아니면 막는다
        assertThatThrownBy(() -> settlement.completeManually(
                admin(2L), "worker-a", "manual-transfer-1", now.plusMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);
    }

    @Test
    void 합계가_포함_항목의_합과_같으면_대사를_통과한다() {
        // given
        WeeklySettlement settlement = settlementWithRevenue();

        // when / then
        assertThatCode(() -> settlement.reconcileWithItemAmounts(
                amount("10000"), amount("0"), amount("0"), amount("10000")))
                .doesNotThrowAnyException();
    }

    @Test
    void 합계가_포함_항목의_합과_다르면_지급하지_않는다() {
        // given — 증분으로 유지해 온 합계가 항목 원본과 어긋났다
        WeeklySettlement settlement = settlementWithRevenue();

        // when / then
        assertThatThrownBy(() -> settlement.reconcileWithItemAmounts(
                amount("9000"), amount("0"), amount("0"), amount("9000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_AMOUNT_MISMATCH);
    }

    @Test
    void 항목_합계의_금액_구성이_깨져_있으면_대사에서_막는다() {
        // given — payout != payment - pg - platform
        WeeklySettlement settlement = settlementWithRevenue();

        // when / then
        assertThatThrownBy(() -> settlement.reconcileWithItemAmounts(
                amount("10000"), amount("100"), amount("0"), amount("10000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_AMOUNT);
    }

    private WeeklySettlement settlementWithRevenue() {
        WeeklySettlement settlement = createSettlement();
        OwnerRevenue revenue = mock(OwnerRevenue.class);
        when(revenue.getStatus()).thenReturn(OwnerRevenueStatus.SETTLEMENT_PENDING);
        when(revenue.getStore()).thenReturn(mock(Store.class));
        when(revenue.getPaymentAmount()).thenReturn(amount("10000"));
        when(revenue.getPgFeeAmount()).thenReturn(amount("0"));
        when(revenue.getPlatformFeeAmount()).thenReturn(amount("0"));
        when(revenue.getPayoutAmount()).thenReturn(amount("10000"));
        settlement.addRevenue(revenue);
        return settlement;
    }

    private WeeklySettlement createSettlement() {
        return WeeklySettlement.create(
                mock(Store.class),
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 8, 0, 0),
                "settlement-1");
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private Account admin(Long accountId) {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);
        return account;
    }
}
