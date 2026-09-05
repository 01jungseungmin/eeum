package com.eeum.eeum.common.util;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.security.CustomUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// SecurityContext에서 현재 로그인한 사용자의 accountId를 추출하는 유틸 클래스
public final class SecurityUtil {

    private SecurityUtil() {
        // 유틸 클래스이므로 인스턴스 생성을 막는다.
    }

    // 현재 로그인한 회원의 accountId를 반환
    // 인증 정보가 없거나 비로그인 상태면 COMMON_UNAUTHORIZED 예외

    public static Long getCurrentAccountId() {
        Authentication authentication = getAuthentication();

        if (!isValidAuthentication(authentication)) {
            throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getAccountId();
        }

        throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED);
    }

    public static Long getCurrentTokenVersion() {
        Authentication authentication = getAuthentication();
        if (!isValidAuthentication(authentication)
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED);
        }
        return userDetails.getTokenVersion();
    }

    // 로그인 여부를 반환 비회원 접근이 허용되는 API에서 로그인 여부만 확인할 때 사용

    public static boolean isAuthenticated() {
        Authentication authentication = getAuthentication();
        return isValidAuthentication(authentication)
                && authentication.getPrincipal() instanceof CustomUserDetails;
    }

    // 재 로그인한 회원의 accountId를 반환 비로그인 상태면 null을 반환

    public static Long getCurrentAccountIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getAccountId();
        }

        return null;
    }

    private static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static boolean isValidAuthentication(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
