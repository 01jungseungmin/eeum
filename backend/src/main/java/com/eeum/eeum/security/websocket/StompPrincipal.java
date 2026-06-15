package com.eeum.eeum.security.websocket;

import java.security.Principal;

// WebSocket(STOMP) 연결에서 인증된 사용자를 나타내는 Principal
// @MessageMapping 핸들러에서 Principal.getName()으로 accountId 추출
public record StompPrincipal(Long accountId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(accountId);
    }
}
