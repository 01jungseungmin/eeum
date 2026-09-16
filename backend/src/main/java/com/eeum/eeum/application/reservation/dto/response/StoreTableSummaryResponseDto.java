package com.eeum.eeum.application.reservation.dto.response;

import com.eeum.eeum.domain.reservation.repository.CapacityCountProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "테이블 구성 요약 응답 (수용 인원별 집계)")
public class StoreTableSummaryResponseDto {

    @Schema(description = "활성 테이블 총 개수", example = "12")
    private long totalCount;

    @Schema(description = "수용 인원별 테이블 개수 (capacity 오름차순)")
    private List<CapacityCountDto> capacityCounts;

    // 집계 쿼리 결과가 capacity 오름차순으로 정렬되어 있음을 전제로 한다
    public static StoreTableSummaryResponseDto from(List<CapacityCountProjection> aggregates) {
        List<CapacityCountDto> capacityCounts = aggregates.stream()
                .map(aggregate -> CapacityCountDto.builder()
                        .capacity(aggregate.getCapacity())
                        .count(aggregate.getCount())
                        .build())
                .toList();

        long totalCount = aggregates.stream()
                .mapToLong(CapacityCountProjection::getCount)
                .sum();

        return StoreTableSummaryResponseDto.builder()
                .totalCount(totalCount)
                .capacityCounts(capacityCounts)
                .build();
    }

    @Getter
    @Builder
    @Schema(description = "수용 인원별 테이블 개수")
    public static class CapacityCountDto {

        @Schema(description = "수용 인원", example = "4")
        private int capacity;

        @Schema(description = "테이블 개수", example = "6")
        private long count;
    }
}
