package com.eeum.eeum.application.ai.dto.request;

import com.eeum.eeum.domain.ai.enums.AiChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "고객 케어 초안 생성 요청")
public class AiCustomerCareDraftRequestDto {

    @Schema(description = "발송 채널 (기본 APP_PUSH)", example = "APP_PUSH")
    private AiChannel channel;

    @Size(max = 100)
    @Schema(description = "문구에 반영할 힌트 (예: 대표 메뉴명)", example = "김치찌개 세트")
    private String contextHint;
}
