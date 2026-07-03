package com.eeum.eeum.domain.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiDataSourceType {
    PRECISE_MEASURED("실측 데이터", "계측 장비 기반 실측값입니다."),
    OWNER_INPUT("사장님 입력값", "사장님이 직접 입력한 실측값 기반입니다."),
    LOCAL_AVERAGE_ONLY("지역 평균 추정", "동네 업종 평균 기반 추정치입니다. 실측값을 입력하면 더 정확해집니다.");

    private final String sourceLabel;
    private final String description;
}
