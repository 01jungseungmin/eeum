package com.eeum.eeum.domain.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiDataSourceType {
    PRECISE_MEASURED("실측 데이터", "계측 장비 기반 실측값입니다."),
    OWNER_INPUT("사장님 입력값", "사장님이 직접 입력한 실측값 기반입니다."),
    PUBLIC_DATA("공공데이터", "공공데이터포털 OpenAPI 기반 통계입니다."),
    FILE_IMPORTED_DATA("공공데이터(파일)", "기관 공개 파일데이터를 내부에 적재한 통계입니다. 최근 공개된 파일 기준입니다."),
    LOCAL_AVERAGE_ONLY("지역 평균 추정", "동네 업종 평균 기반 추정치입니다. 실측값을 입력하면 더 정확해집니다."),
    UNAVAILABLE("데이터 없음", "현재 조회 가능한 데이터가 없습니다.");

    private final String sourceLabel;
    private final String description;
}
