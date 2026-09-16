package com.eeum.eeum.application.store.dto.request;

import com.eeum.eeum.domain.store.enums.StoreStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "상점 상태 변경 요청")
public class StoreStatusUpdateRequestDto {

    @Schema(
            description = "변경할 상점 상태. 사장 화면에서는 OPEN 또는 TEMP_CLOSED만 허용합니다.",
            example = "OPEN",
            allowableValues = {"OPEN", "TEMP_CLOSED"}
    )
    @NotNull(message = "상점 상태는 필수입니다.")
    private StoreStatus status;
}