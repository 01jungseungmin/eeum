package com.eeum.eeum.application.reservation.dto.response;

import com.eeum.eeum.domain.reservation.entity.StoreTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Getter
@Builder
@Schema(description = "테이블 구성 요약 응답 (수용 인원별 집계)")
public class StoreTableSummaryResponseDto {

    @Schema(description = "활성 테이블 총 개수", example = "12")
    private long totalCount;

    @Schema(description = "수용 인원별 테이블 개수 (capacity 오름차순)")
    private List<CapacityCountDto> capacityCounts;

    public static StoreTableSummaryResponseDto from(List<StoreTable> tables) {
        // TreeMap으로 capacity 오름차순 정렬 보장
        Map<Integer, Long> countByCapacity = tables.stream()
                .collect(Collectors.groupingBy(StoreTable::getCapacity, TreeMap::new, Collectors.counting()));

        List<CapacityCountDto> capacityCounts = countByCapacity.entrySet().stream()
                .map(entry -> CapacityCountDto.builder()
                        .capacity(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();

        return StoreTableSummaryResponseDto.builder()
                .totalCount(tables.size())
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
