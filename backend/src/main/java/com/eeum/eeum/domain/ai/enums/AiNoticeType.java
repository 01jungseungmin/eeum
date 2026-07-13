package com.eeum.eeum.domain.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiNoticeType {
    EVENT("이벤트 안내"),
    TEMP_CLOSED("임시 휴무 안내"),
    NEW_MENU("신메뉴 소식");

    private final String displayName;
}
