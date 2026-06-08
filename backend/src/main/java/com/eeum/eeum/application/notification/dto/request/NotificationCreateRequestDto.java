package com.eeum.eeum.application.notification.dto.request;

import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

/**
 * 알림 생성 요청 DTO.
 * 다른 도메인 서비스(OrderService, StoreReviewService 등)에서 호출할 때 사용한다.
 * REST API 노출 없이 내부 서비스 간 호출 전용.
 */
@Getter
@Builder
public class NotificationCreateRequestDto {

    private Long accountId;
    private NotificationType type;
    private String title;
    private String content;
    private NotificationRefType refType;
    private Long refId;
    private String linkUrl;
}
