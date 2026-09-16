package com.eeum.eeum.infrastructure.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

// prod/demo 외 환경(local, dev 등)에서 활성화되는 no-op 폴백
@Slf4j
@Component
@Profile("!prod & !demo")
public class NoOpPushAdapter implements PushAdapter {

    @Override
    public PushResult send(PushMessage message) {
        // 고객 발송 문구(title/body) 전문은 로그에 남기지 않는다 — 길이만 info, 미리보기는 debug로
        log.info("[NoOpPushAdapter] 푸시 발송 스킵: token={}, titleLength={}, bodyLength={}",
                maskToken(message.getFcmToken()),
                length(message.getTitle()),
                length(message.getBody()));
        log.debug("[NoOpPushAdapter] titlePreview={}, bodyPreview={}",
                preview(message.getTitle()), preview(message.getBody()));

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

    private int length(String text) {
        return text != null ? text.length() : 0;
    }

    private String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 20 ? text : text.substring(0, 20) + "...";
    }
}