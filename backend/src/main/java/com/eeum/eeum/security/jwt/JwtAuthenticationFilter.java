package com.eeum.eeum.security.jwt;

import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.security.CustomUserDetails;
import com.eeum.eeum.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                // 1. 토큰 자체 유효성 검증
                if (!jwtProvider.isValid(token)) {
                    log.debug("유효하지 않은 JWT 토큰");
                    filterChain.doFilter(request, response);
                    return;
                }

                // 2. Access Token 타입 확인
                if (!jwtProvider.isAccessToken(token)) {
                    log.debug("Access Token이 아닙니다");
                    filterChain.doFilter(request, response);
                    return;
                }

                // 3. 블랙리스트 확인
                if (tokenService.isBlacklisted(token)) {
                    log.debug("블랙리스트에 등록된 Access Token");
                    filterChain.doFilter(request, response);
                    return;
                }

                // 4. 이미 인증 정보가 없는 경우에만 SecurityContext 등록
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long accountId = jwtProvider.getAccountId(token);

                    CustomUserDetails userDetails =
                            (CustomUserDetails) userDetailsService.loadUserByUsername(
                                    String.valueOf(accountId)
                            );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

            } catch (Exception e) {
                log.debug("JWT 인증 처리 중 오류: {}", e.getMessage());
                // 예외 발생 시 인증 없이 다음 필터로 진행
                // 이후 SecurityConfig에서 인증이 필요한 API라면 401/403 처리됨
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}