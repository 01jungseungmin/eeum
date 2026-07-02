package com.eeum.eeum.application.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "테이블 구성 설정 요청")
public class StoreTableConfigRequestDto {

    @NotEmpty(message = "테이블 목록은 비어있을 수 없습니다.")
    @Valid
    @Schema(description = "테이블 항목 목록")
    private List<TableItem> tables;

    @Getter
    @Schema(description = "테이블 항목")
    public static class TableItem {

        @NotNull
        @Min(value = 1, message = "수용 인원은 1명 이상이어야 합니다.")
        @Schema(description = "수용 인원", example = "4")
        private Integer capacity;

        @NotNull
        @Min(value = 1, message = "테이블 수는 1개 이상이어야 합니다.")
        @Schema(description = "테이블 수", example = "3")
        private Integer count;
    }
}
