package com.eeum.eeum.domain.settlement.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WeeklySettlementTest {

    @Test
    void 만료된_claim은_새_작업자가_재획득할_수_있고_이전_작업자는_완료할_수_없다() {
        // given
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        settlement.claim("worker-a", now.plusMinutes(1), now, now);
        settlement.claim("worker-b", now.plusMinutes(10), now.plusMinutes(2), now.plusMinutes(2));

        // when / then
        assertThatThrownBy(() -> settlement.completeManually(
                mock(Account.class), "worker-a", "manual-transfer-1", now.plusMinutes(3)))
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
        settlement.claim("worker-a", now.plusMinutes(10), now, now);

        // when
        settlement.completeManually(mock(Account.class), "worker-a", "manual-transfer-1", now.plusMinutes(1));

        // then
        assertThat(settlement.getStatus()).isEqualTo(WeeklySettlementStatus.COMPLETED);
        assertThat(settlement.getClaimToken()).isNull();
    }

    @Test
    void 만료된_claim은_실패를_수동검토로_전이할_수_없다() {
        WeeklySettlement settlement = createSettlement();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        settlement.claim("worker-a", now.plusMinutes(1), now, now);
        settlement.markFailed("worker-a", "PAYOUT_FAILED", "실패", now.plusSeconds(30));

        assertThatThrownBy(() -> settlement.requireManualReview(
                "worker-a", "PAYOUT_FAILED", "실패", now.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_CLAIM_MISMATCH);
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
}
