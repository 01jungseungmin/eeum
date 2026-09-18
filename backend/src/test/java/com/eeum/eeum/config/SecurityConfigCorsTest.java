package com.eeum.eeum.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 웹 데모(Vercel)에서 이 API를 부르지 못하면 심사자가 빈 화면을 본다.
// 허용 출처가 코드에 박혀 있던 동안 실제로 그 상태였고, 브라우저에서만 재현돼
// 서버 로그로는 드러나지 않았다. 출처 매칭을 테스트로 고정한다.
class SecurityConfigCorsTest {

    private static final List<String> CONFIGURED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:4173",
            "https://eeum.life",
            "https://*.vercel.app"
    );

    private CorsConfiguration corsConfiguration() {
        SecurityConfig securityConfig = new SecurityConfig(null, null, null);
        ReflectionTestUtils.setField(securityConfig, "allowedOrigins", CONFIGURED_ORIGINS);

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();
        return source.getCorsConfigurations().get("/**");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://eeum-demo.vercel.app",
            "https://eeum-web-demo-git-main-team.vercel.app",
            "http://localhost:4173",
            "https://eeum.life"
    })
    void 웹_데모가_쓰는_출처는_허용된다(String origin) {
        assertThat(corsConfiguration().checkOrigin(origin)).isEqualTo(origin);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com",
            "https://vercel.app.evil.com"
    })
    void 목록에_없는_출처는_차단된다(String origin) {
        assertThat(corsConfiguration().checkOrigin(origin)).isNull();
    }

    @Test
    void 와일드카드와_인증정보_전달이_함께_동작한다() {
        CorsConfiguration config = corsConfiguration();

        // setAllowedOrigins였다면 와일드카드 + allowCredentials 조합에서 요청이 거부된다.
        // Patterns로 설정돼 있어야 둘이 공존한다.
        assertThat(config.getAllowedOriginPatterns()).containsAll(CONFIGURED_ORIGINS);
        assertThat(config.getAllowedOrigins()).isNull();
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
