package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.config.RestClientConfig;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import com.eeum.eeum.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OAuthServiceContractTest {
    private MockRestServiceServer server;
    private OAuthService service;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClientConfig config = mock(RestClientConfig.class);
        when(config.restClient()).thenReturn(builder.build());
        service = new OAuthService(config);
        ReflectionTestUtils.setField(service, "kakaoAppId", 1234L);
    }

    private void tokenInfo(int appId) {
        server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"app_id\":" + appId + ",\"id\":42,\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }

    @Test
    void 다른_앱_토큰은_프로필_조회_전에_거절한다() {
        tokenInfo(9999);
        assertThatThrownBy(() -> service.getUserInfo(OAuthProvider.KAKAO, "token"))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    void 재인증도_다른_앱_토큰을_거절한다() {
        tokenInfo(9999);
        assertThatThrownBy(() -> service.validateToken(OAuthProvider.KAKAO, "token", "42"))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    void 미인증_이메일은_계정_식별정보로_사용하지_않는다() {
        tokenInfo(1234);
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("""
                        {"id":42,"kakao_account":{"email":"other@example.com",
                        "is_email_valid":true,"is_email_verified":false}}
                        """, MediaType.APPLICATION_JSON));
        var info = service.getUserInfo(OAuthProvider.KAKAO, "token");
        assertThat(info.getProviderId()).isEqualTo("42");
        assertThat(info.getEmail()).isNull();
        assertThat(info.isEmailVerified()).isFalse();
        server.verify();
    }

    @Test
    void 유효한_앱과_검증된_이메일만_사용한다() {
        tokenInfo(1234);
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("""
                        {"id":42,"kakao_account":{"email":"user@example.com",
                        "is_email_valid":true,"is_email_verified":true}}
                        """, MediaType.APPLICATION_JSON));
        var info = service.getUserInfo(OAuthProvider.KAKAO, "token");
        assertThat(info.getEmail()).isEqualTo("user@example.com");
        assertThat(info.isEmailVerified()).isTrue();
        server.verify();
    }
}
