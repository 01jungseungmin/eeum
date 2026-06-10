package com.eeum.eeum.infrastructure.push;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

//Firebase 서비스 계정 키를 이용해 Google OAuth2 액세스 토큰을 발급
//토큰 캐싱 — 액세스 토큰 유효 기간(1시간)보다 5분 일찍 갱신해 매 요청마다 Google API 호출을 방지
@Slf4j
@Component
public class FcmTokenProvider {

    private static final List<String> SCOPES =
            List.of("https://www.googleapis.com/auth/firebase.messaging");

    // 토큰 만료 5분 전 갱신
    private static final long REFRESH_BUFFER_SECONDS = 300;

    @Value("${fcm.service-account-key-path}")
    private Resource serviceAccountKeyResource;

    private String cachedAccessToken;
    private Instant tokenExpiresAt;

    //유효한 액세스 토큰을 반환 캐시된 토큰이 만료 임박이면 Google API로 재발급
    public String getAccessToken() throws IOException {
        if (isTokenValid()) {
            return cachedAccessToken;
        }
        return refreshToken();
    }

    private boolean isTokenValid() {
        return cachedAccessToken != null
                && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt.minusSeconds(REFRESH_BUFFER_SECONDS));
    }

    private String refreshToken() throws IOException {

        GoogleCredentials credentials = GoogleCredentials
             .fromStream(serviceAccountKeyResource.getInputStream())
             .createScoped(SCOPES);
         credentials.refreshIfExpired();
         AccessToken token = credentials.getAccessToken();
         cachedAccessToken = token.getTokenValue();
         tokenExpiresAt    = token.getExpirationTime().toInstant();
         log.debug("FCM 액세스 토큰 갱신 완료");
         return cachedAccessToken;
    }
}
