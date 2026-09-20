package com.eeum.eeum.domain.store.enums;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankTest {

    // 이 목록이 깨지면 기존 정산 계좌 화면이 400을 받는다
    @DisplayName("프론트 셀렉트 박스의 은행명을 그대로 받는다")
    @ParameterizedTest
    @ValueSource(strings = {"국민은행", "신한은행", "우리은행", "하나은행", "기업은행", "농협은행", "카카오뱅크", "토스뱅크"})
    void 프론트_은행명을_그대로_받는다(String bankName) {
        assertThat(Bank.normalizeName(bankName)).isEqualTo(bankName);
    }

    @Test
    void 별칭은_대표_이름으로_모인다() {
        assertThat(Bank.normalizeName("KB국민")).isEqualTo("국민은행");
        assertThat(Bank.normalizeName("국민")).isEqualTo("국민은행");
        assertThat(Bank.normalizeName("IBK기업")).isEqualTo("기업은행");
        assertThat(Bank.normalizeName("NH농협")).isEqualTo("농협은행");
        assertThat(Bank.normalizeName("iM뱅크(대구)")).isEqualTo("iM뱅크");
    }

    @Test
    void 공백과_대소문자는_무시한다() {
        assertThat(Bank.normalizeName(" 국민 은행 ")).isEqualTo("국민은행");
        assertThat(Bank.normalizeName("IM뱅크")).isEqualTo("iM뱅크");
    }

    @Test
    void enum_상수명으로도_찾는다() {
        assertThat(Bank.find("KB")).contains(Bank.KB);
    }

    @Test
    void 목록에_없는_은행은_거부한다() {
        assertThatThrownBy(() -> Bank.normalizeName("없는은행"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_BANK_NOT_SUPPORTED);
    }

    @Test
    void 빈_값은_빈_결과다() {
        assertThat(Bank.find(null)).isEmpty();
        assertThat(Bank.find("  ")).isEmpty();
    }

    // 별칭이 다른 은행의 대표 이름을 덮으면 저장 값이 엉뚱한 은행으로 바뀐다
    @Test
    void 대표_이름은_별칭에_가려지지_않는다() {
        for (Bank bank : Bank.values()) {
            assertThat(Bank.from(bank.getDisplayName())).isEqualTo(bank);
        }
    }

    @Test
    void 기관코드는_아직_비어있다() {
        assertThat(Bank.values()).allSatisfy(bank ->
                assertThat(bank.getInstitutionCode()).isNull());
    }
}
