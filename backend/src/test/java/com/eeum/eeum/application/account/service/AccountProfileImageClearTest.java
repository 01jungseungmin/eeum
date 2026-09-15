package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증상: 프로필 사진을 지우려고 빈 값을 보내면 "파일 접근 권한이 없습니다" 403이 났다.
 * 결함 위치: Account.updateInfo가 null만 "변경 안 함"으로 보고 빈 문자열을 그대로 저장하던 점,
 * 그리고 AccountService가 빈 값을 첨부 검증 경로로 보내던 점.
 * null(변경 안 함)과 빈 문자열(지우기)을 구분한다.
 */
class AccountProfileImageClearTest {

    @Test
    void 빈_값은_프로필_사진을_지운다() {
        // Given
        Account account = accountWithProfileImage();

        // When
        account.updateInfo(null, "");

        // Then
        assertThat(account.getProfileImageUrl()).isNull();
    }

    @Test
    void null은_프로필_사진을_바꾸지_않는다() {
        // Given
        Account account = accountWithProfileImage();

        // When — 닉네임만 바꾸는 요청이다.
        account.updateInfo("새닉네임", null);

        // Then
        assertThat(account.getNickname()).isEqualTo("새닉네임");
        assertThat(account.getProfileImageUrl()).isEqualTo("profiles/42/a.webp");
    }

    private Account accountWithProfileImage() {
        Account account = Account.createUser(
                "user@test.com", "encoded_pw", "이름", "닉네임", "010-1111-2222");
        account.updateInfo(null, "profiles/42/a.webp");
        return account;
    }
}
