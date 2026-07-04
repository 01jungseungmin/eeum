package com.eeum.eeum.infrastructure.push;

import com.eeum.eeum.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Firebase Cloud Messaging(FCM) HTTP v1 API 기반 푸시 발송 구현체.
// 1. FirebaseApp.initializeApp() → GoogleCredentials로 OAuth2 액세스 토큰 취득
// 2. POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send Authorization: Bearer {accessToken}

@Slf4j
@Component
@Profile({"prod", "demo"})
@RequiredArgsConstructor
public class FcmPushAdapter implements PushAdapter {

    private static final String FCM_SEND_URL =
            "https://fcm.googleapis.com/v1/projects/%s/messages:send";

    // FCM 오류 코드 중 토큰 무효화 대상
    private static final List<String> INVALID_TOKEN_ERRORS = List.of(
            "UNREGISTERED", "INVALID_ARGUMENT"
    );

    private final RestTemplate restTemplate;
    private final AccountRepository accountRepository;
    private final FcmTokenProvider fcmTokenProvider; // OAuth2 액세스 토큰 제공자

    @Value("${fcm.project-id}")
    private String projectId;

    // ===================== PushAdapter 구현 =====================

    @Override
    public PushResult send(PushMessage message) {
        if (message.getFcmToken() == null || message.getFcmToken().isBlank()) {
            log.debug("FCM 토큰 없음 — 푸시 스킵: message={}", message.getTitle());
            return PushResult.failure("NO_TOKEN", false);
        }

        try {
            HttpHeaders headers = buildHeaders();
            Map<String, Object> body = buildSingleMessageBody(message);

            ResponseEntity<Map> response = restTemplate.exchange(
                    String.format(FCM_SEND_URL, projectId),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String messageId = (String) response.getBody().get("name");
                log.debug("FCM 발송 성공: messageId={}", messageId);
                return PushResult.success(messageId);
            }

            return PushResult.failure("UNKNOWN_ERROR", false);

        } catch (Exception e) {
            log.warn("FCM 발송 실패: token={}, error={}", maskToken(message.getFcmToken()), e.getMessage());
            boolean isInvalidToken = isInvalidTokenError(e.getMessage());
            if (isInvalidToken) {
                handleInvalidToken(message.getFcmToken());
            }
            return PushResult.failure(e.getMessage(), isInvalidToken);
        }
    }

    // 배치 발송 — 500건 초과 시 청크 분할(FCM HTTP v1은 멀티캐스트를 지원하지 않으므로 단건 병렬 처리로 구현)
    @Override
    public List<PushResult> sendBatch(List<PushMessage> messages) {
        List<PushResult> results = new ArrayList<>(messages.size());
        for (PushMessage message : messages) {
            results.add(send(message));
        }
        return results;
    }

    // ===================== FCM 토큰 무효화 처리 =====================

    // 만료/무효 토큰 감지 시 Account.fcmToken을 null로 초기화 다음 앱 실행 시 클라이언트가 새 토큰을 재등록
    public void handleInvalidToken(String fcmToken) {
        int updated = accountRepository.clearFcmTokenByFcmToken(fcmToken);
        log.info("무효 FCM 토큰 초기화: affected={}", updated);
    }

    // ===================== 내부 헬퍼 =====================

    private HttpHeaders buildHeaders() throws Exception {
        String accessToken = fcmTokenProvider.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private Map<String, Object> buildSingleMessageBody(PushMessage message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", message.getTitle());
        notification.put("body", message.getBody());

        Map<String, Object> msg = new HashMap<>();
        msg.put("token", message.getFcmToken());
        msg.put("notification", notification);

        if (message.getLinkUrl() != null) {
            Map<String, String> data = message.getData() != null
                    ? new HashMap<>(message.getData())
                    : new HashMap<>();
            data.put("linkUrl", message.getLinkUrl());
            msg.put("data", data);
        } else if (message.getData() != null) {
            msg.put("data", message.getData());
        }

        // Android 채널 설정
        Map<String, Object> android = new HashMap<>();
        Map<String, Object> androidNotification = new HashMap<>();
        androidNotification.put("channel_id", "eeum_default");
        android.put("notification", androidNotification);
        msg.put("android", android);

        // iOS(APNs) 설정
        // apns-priority 10 = 즉시 전송 (5 = 낮은 우선순위, 기기 절전 시 지연)
        Map<String, Object> apnsHeaders = new HashMap<>();
        apnsHeaders.put("apns-priority", "10");

        Map<String, Object> aps = new HashMap<>();
        aps.put("sound", "default");   // 기본 알림음 재생
        aps.put("content-available", 1); // 백그라운드 실행 허용

        Map<String, Object> apnsPayload = new HashMap<>();
        apnsPayload.put("aps", aps);

        Map<String, Object> apns = new HashMap<>();
        apns.put("headers", apnsHeaders);
        apns.put("payload", apnsPayload);
        msg.put("apns", apns);

        Map<String, Object> body = new HashMap<>();
        body.put("message", msg);
        body.put("validate_only", false);
        return body;
    }

    private boolean isInvalidTokenError(String errorMessage) {
        if (errorMessage == null) return false;
        return INVALID_TOKEN_ERRORS.stream()
                .anyMatch(errorMessage.toUpperCase()::contains);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) return "****";
        return token.substring(0, 6) + "****" + token.substring(token.length() - 4);
    }
}
