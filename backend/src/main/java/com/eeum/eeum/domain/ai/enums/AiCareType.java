package com.eeum.eeum.domain.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiCareType {
    CART_INTEREST("구매 관심이 높은 고객", 1),
    INACTIVE_REGULAR("한동안 방문이 없는 단골", 2),
    INQUIRY_HESITATION("문의 후 망설이는 고객", 3);

    private final String title;
    private final int priority;
}
