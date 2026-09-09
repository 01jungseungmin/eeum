package com.eeum.eeum.application.search.service;

import com.eeum.eeum.common.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 인기 검색어 중복 집계용 식별자다.
 *
 * <p>{@code X-Forwarded-For}는 신뢰 프록시 경계가 따로 설정되지 않은 상태에서 클라이언트가
 * 임의로 넣을 수 있으므로 사용하지 않는다. 그 값을 그대로 쓰면 익명 사용자가 요청마다 다른 값을
 * 보내 10분 중복 제한을 우회할 수 있다.
 */
@Component
public class PopularSearchViewerKeyResolver {

    public String resolve(HttpServletRequest request) {
        Long accountId = SecurityUtil.getCurrentAccountIdOrNull();
        if (accountId != null) {
            return "account:" + accountId;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
