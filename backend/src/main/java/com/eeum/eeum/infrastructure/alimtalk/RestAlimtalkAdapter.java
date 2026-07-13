package com.eeum.eeum.infrastructure.alimtalk;

import com.eeum.eeum.common.util.MaskingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 실제 알림톡 Provider 어댑터 (REST 기반 — alimtalk.provider=rest 설정 시 활성).
 * 인증 정보(api-key/secret/sender-key)가 없으면 발송하지 않고 실패 처리한다.
 * 알림톡은 승인 템플릿 기반 — AI 문구는 템플릿 변수로 전달하며,
 * Provider별 자유 문구 허용 정책은 계약 시 확인이 필요하다.
 * 주의: API Key/Secret/전화번호 원문은 로그에 남기지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "alimtalk.provider", havingValue = "rest")
public class RestAlimtalkAdapter implements AlimtalkAdapter {

    private final RestClient restClient;
    private final AlimtalkProperties properties;

    public RestAlimtalkAdapter(@Qualifier("aiRestClient") RestClient restClient, AlimtalkProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public AlimtalkResult send(AlimtalkMessage message) {
        if (!properties.hasCredentials() || properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            log.warn("[Alimtalk] 인증 정보 미설정 — 발송 비활성");
            return AlimtalkResult.fail("CREDENTIALS_NOT_CONFIGURED");
        }
        if (message.templateCode() == null || message.templateCode().isBlank()) {
            return AlimtalkResult.fail("TEMPLATE_NOT_CONFIGURED");
        }
        try {
            Map<String, Object> body = Map.of(
                    "senderKey", properties.senderKey(),
                    "templateCode", message.templateCode(),
                    "recipientList", List.of(Map.of(
                            "recipientNo", message.phone(),
                            "templateParameter", Map.of(
                                    "title", message.title() != null ? message.title() : "",
                                    "content", message.content()))));
            restClient.post()
                    .uri(properties.baseUrl())
                    .header("X-Secret-Key", properties.apiSecret())
                    .header("X-Api-Key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[Alimtalk] 발송 성공: phone={}, template={}",
                    MaskingUtil.maskPhone(message.phone()), message.templateCode());
            return AlimtalkResult.ok("alimtalk-" + System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("[Alimtalk] 발송 실패: {}", e.getClass().getSimpleName());
            return AlimtalkResult.fail(e.getClass().getSimpleName());
        }
    }
}
