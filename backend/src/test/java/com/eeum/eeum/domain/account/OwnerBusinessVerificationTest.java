package com.eeum.eeum.domain.account;

import com.eeum.eeum.application.account.service.OwnerBusinessSnapshotReader;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class OwnerBusinessVerificationTest {
    @Test
    void 입력값만으로는_검증완료가_되지_않으며_번호변경은_증빙을_무효화한다() {
        Account account = Account.createOwner("a@test.com", "pw", "대표자", "010");
        OwnerInfo info = OwnerInfo.create(account, "1234567890", LocalDate.of(2020, 1, 1));
        assertThat(info.isBusinessVerified()).isFalse();
        info.markBusinessVerified("대표자");
        assertThat(info.isBusinessVerified()).isTrue();
        info.updateInfo("9999999999");
        assertThat(info.isBusinessVerified()).isFalse();
    }

    @Test
    void 검증중_번호가_변경되면_이전_증빙을_반영할_수_없다() {
        Account account = Account.createOwner("a@test.com", "pw", "대표자", "010");
        LocalDate date = LocalDate.of(2020, 1, 1);
        OwnerInfo info = OwnerInfo.create(account, "1234567890", date);
        var snapshot = new OwnerBusinessSnapshotReader.Snapshot("1234567890", "대표자", date);
        info.updateInfo("9999999999");
        assertThatThrownBy(() -> snapshot.assertMatches(info)).isInstanceOf(BusinessException.class);
    }
}
