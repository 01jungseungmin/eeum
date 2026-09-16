package com.eeum.eeum.application.notification.dto.response;

import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@Schema(description = "안 읽은 알림 수 응답 (앱/웹 배지)")
public class UnreadCountResponseDto {

    @Schema(description = "안 읽은 알림 수 (전체)", example = "3")
    private long unreadCount;

    @Schema(description = "카테고리별 안 읽은 알림 수 (모든 카테고리 포함, 없으면 0)",
            example = "{\"ORDER\": 2, \"CHAT\": 1, \"REVIEW\": 0, \"RESERVATION\": 0, \"PRODUCT\": 0, \"COMMUNITY\": 0, \"SYSTEM\": 0}")
    private Map<NotificationCategory, Long> byCategory;

    public static UnreadCountResponseDto of(long count, Map<NotificationCategory, Long> byCategory) {
        return UnreadCountResponseDto.builder()
                .unreadCount(count)
                .byCategory(byCategory)
                .build();
    }
}
