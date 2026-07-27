package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "데이터 출처")
    public class DataSourceDto {
        @Schema(description = "출처 타입", example = "OWNER_INPUT")
        private final AiDataSourceType sourceType;
        @Schema(description = "출처 라벨", example = "사장님 입력값")
        private final String sourceLabel;
        @Schema(description = "설명")
        private final String description;

        public static DataSourceDto from(AiDataSourceType type) {
            return DataSourceDto.builder()
                    .sourceType(type)
                    .sourceLabel(type.getSourceLabel())
                    .description(type.getDescription())
                    .build();
        }
    }