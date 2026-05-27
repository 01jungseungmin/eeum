package com.eeum.eeum.config;

import com.eeum.eeum.security.jwt.JwtAccessDeniedHandler;
import com.eeum.eeum.security.jwt.JwtAuthenticationEntryPoint;
import com.eeum.eeum.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // ===================== 인증 없이 허용할 GET 경로 =====================
    private static final String[] PUBLIC_GET = {
            "/stores/**",
            "/used-products/**",
            "/products/**",
            "/regions/search",
            "/regions/nearby",
            "/event-products/**"
    };

    // ===================== 인증 없이 허용할 POST 경로 =====================
    private static final String[] PUBLIC_POST = {
            "/auth/email/send-verification",
            "/auth/email/verify",
            "/auth/signup",
            "/auth/signup/owner",
            "/auth/login",
            "/auth/login/oauth",
            "/auth/signup/oauth",
            "/auth/token/reissue",
            "/auth/password/reset-request",
            "/auth/password/verify",
            "/auth/password/reset",
            "/auth/business/verify",
            "/admin/locations/sync"
    };

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**"
    };

    private static final String[] ACTUATOR_PATHS = {
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT Stateless 방식이므로 CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // CORS 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션 미사용
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 기본 로그인 방식 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 인증/인가 예외 처리
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                // URL 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        .requestMatchers(ACTUATOR_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()


                        // PortOne Webhook은 JWT 인증 대신 서명 검증으로 처리
                        .requestMatchers(HttpMethod.POST, "/payments/webhook").permitAll()

                        // 사장 승인 전 추가 입력/심사 요청 API
                        .requestMatchers(
                                "/owner/stores/me/checklist",
                                "/owner/stores/me/business-info",
                                "/owner/stores/me/settlement-account",
                                "/owner/stores/me/apply",
                                "/owner/stores/me/representative-menu"
                        ).hasAnyRole("USER", "OWNER")

                        // 관리자 전용
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // 사장 전용
                        .requestMatchers("/owner/**").hasRole("OWNER")

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 등록
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:8081",
                "http://localhost:5173",
                "https://eeum.com",
                "https://www.eeum.com"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}