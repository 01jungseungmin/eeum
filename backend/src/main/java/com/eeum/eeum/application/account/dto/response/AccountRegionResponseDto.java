package com.eeum.eeum.application.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "활동 지역 응답")
public class AccountRegionResponseDto {

    @Schema(description = "활동 지역 ID", example = "1")
    private Long accountRegionId;

    @Schema(description = "지역 ID", example = "1")
    private Long regionId;

    @Schema(description = "시/도", example = "서울특별시")
    private String siDo;

    @Schema(description = "시/군/구", example = "강남구")
    private String gunGu;

    @Schema(description = "행정동", example = "역삼동")
    private String dong;

    // 글쓰기 화면에서 지도를 열 때의 초기 중심·반경이다.
    // 좌표는 location 테이블에서 오는데 아직 등록되지 않은 지역이 있을 수 있어 null이 가능하다.
    // 클라이언트는 null이면 자체 기본 중심으로 대체한다.
    @Schema(description = "지역 중심 위도. 좌표가 등록되지 않은 지역이면 null", example = "37.500123")
    private Double latitude;

    @Schema(description = "지역 중심 경도. 좌표가 등록되지 않은 지역이면 null", example = "127.036456")
    private Double longitude;

    @Schema(description = "지역 반경(미터). 지도 반경 표시에 쓴다", example = "3000")
    private Integer radius;

    @Schema(description = "대표 지역 여부", example = "true")
    private Boolean isPrimary;

    @Schema(description = "GPS 인증 여부", example = "true")
    private Boolean verified;

    @Schema(description = "인증 일시", example = "2026-05-06T12:00:00")
    private LocalDateTime verifiedAt;

    @Schema(description = "등록 일시", example = "2026-05-06T12:00:00")
    private LocalDateTime createdAt;
}
