package com.eeum.eeum.application.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "안 읽은 알림 수 응답 (앱 배지)")
public class UnreadCountResponseDto {

    @Schema(description = "안 읽은 알림 수", example = "3")
    private long unreadCount;

    public static UnreadCountResponseDto of(long count) {
        return UnreadCountResponseDto.builder().unreadCount(count).build();
    }
}
