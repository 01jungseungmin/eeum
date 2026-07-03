package com.eeum.eeum.application.reservation.dto.response;

import com.eeum.eeum.domain.reservation.entity.StoreTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "테이블 정보 응답")
public class StoreTableResponseDto {

    @Schema(description = "테이블 ID")
    private Long storeTableId;

    @Schema(description = "수용 인원")
    private int capacity;

    @Schema(description = "테이블 이름", example = "2인석-1")
    private String tableName;

    @Schema(description = "활성화 여부")
    private boolean active;

    public static StoreTableResponseDto from(StoreTable table) {
        return StoreTableResponseDto.builder()
                .storeTableId(table.getStoreTableId())
                .capacity(table.getCapacity())
                .tableName(table.getTableName())
                .active(table.isActive())
                .build();
    }
}
