package com.eeum.eeum.infrastructure.alimtalk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림톡 Provider 설정. 기본 provider는 mock — 인증 정보 없이 서버 실행 가능.
 * 알림톡은 승인된 템플릿 기반이므로 메시지 유형별 템플릿 코드를 설정으로 관리한다.
 */
@ConfigurationProperties(prefix = "alimtalk")
public record AlimtalkProperties(
        String provider,
        String apiKey,
        String apiSecret,
        String senderKey,
        String baseUrl,
        Templates templates
) {
    public AlimtalkProperties {
        provider = (provider == null || provider.isBlank()) ? "mock" : provider;
        templates = templates != null ? templates : new Templates(null, null, null);
    }

    public boolean hasCredentials() {
        return apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank()
                && senderKey != null && !senderKey.isBlank();
    }

    public record Templates(String customerCare, String marketing, String notice) {
    }
}
