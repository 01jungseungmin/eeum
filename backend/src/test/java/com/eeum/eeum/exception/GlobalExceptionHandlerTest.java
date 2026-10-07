package com.eeum.eeum.exception;

import com.eeum.eeum.common.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 사업자번호_unique_제약_경합은_정확한_409으로_응답한다() {
        var response = handler.handleDataIntegrityViolation(
                duplicateKey("uk_owner_info_business_number"));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER.getHttpStatus());
        assertThat(((ApiResponse<?>) response.getBody()).getError().getCode())
                .isEqualTo(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER.getCode());
    }

    @Test
    void OAuth_계정_unique_제약_경합은_정확한_409으로_응답한다() {
        var response = handler.handleDataIntegrityViolation(duplicateKey("uk_account_provider"));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.ACCOUNT_ALREADY_EXISTS.getHttpStatus());
        assertThat(((ApiResponse<?>) response.getBody()).getError().getCode())
                .isEqualTo(ErrorCode.ACCOUNT_ALREADY_EXISTS.getCode());
    }

    private DataIntegrityViolationException duplicateKey(String constraint) {
        return new DataIntegrityViolationException(
                "duplicate key", new SQLException("Duplicate entry for key '" + constraint + "'"));
    }
}
