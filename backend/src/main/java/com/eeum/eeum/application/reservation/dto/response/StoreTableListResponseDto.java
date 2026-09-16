package com.eeum.eeum.application.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "테이블 구성 목록 및 요약 응답")
public class StoreTableListResponseDto {

    @Schema(description = "테이블 목록")
    private List<StoreTableResponseDto> tables;

    @Schema(description = "테이블 구성 요약")
    private StoreTableSummaryResponseDto summary;

    public static StoreTableListResponseDto of(
            List<StoreTableResponseDto> tables,
            StoreTableSummaryResponseDto summary
    ) {
        return StoreTableListResponseDto.builder()
                .tables(tables)
                .summary(summary)
                .build();
    }
}