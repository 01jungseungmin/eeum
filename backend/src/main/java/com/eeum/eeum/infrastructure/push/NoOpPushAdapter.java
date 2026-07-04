package com.eeum.eeum.infrastructure.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

// FcmPushAdapter(prod/demo)가 없는 환경에서 자동 활성화되는 no-op 폴백
@Slf4j
@Component
@Profile("!prod & !demo")
public class NoOpPushAdapter implements PushAdapter {

    @Override
    public PushResult send(PushMessage message) {
        log.info("[NoOpPushAdapter] 푸시 발송 스킵: token={}, title={}, body={}",
                maskToken(message.getFcmToken()),
                message.getTitle(),
                message.getBody());

        return PushResult.success("noop-message-id");
    }

    @Override
    public List<PushResult> sendBatch(List<PushMessage> messages) {
        return messages.stream()
                .map(this::send)
                .toList();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "****";
        }
        return token.substring(0, 6) + "****" + token.substring(token.length() - 4);
    }
}