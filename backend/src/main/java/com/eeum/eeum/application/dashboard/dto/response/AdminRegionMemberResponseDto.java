package com.eeum.eeum.application.dashboard.dto.response;

import com.eeum.eeum.domain.account.repository.RegionMemberCount;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "구·군별 동네 인증 회원 수")
public class AdminRegionMemberResponseDto {

    @Schema(description = "시·도", example = "서울특별시")
    private final String siDo;

    @Schema(description = "구·군. 구·군이 없는 시(세종 등)는 null", example = "강남구", nullable = true)
    private final String gunGu;

    @Schema(description = "대표 동네가 이 구·군에 있고 동네 인증을 마친 회원 수", example = "2842")
    private final long memberCount;

    public static AdminRegionMemberResponseDto from(RegionMemberCount row) {
        return AdminRegionMemberResponseDto.builder()
                .siDo(row.siDo())
                .gunGu(row.gunGu())
                .memberCount(row.memberCount())
                .build();
    }
}
