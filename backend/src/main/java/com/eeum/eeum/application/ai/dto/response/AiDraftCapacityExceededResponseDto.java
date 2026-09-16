package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "초안 보관 개수 캡 초과 응답 (AI_015)")
public class AiDraftCapacityExceededResponseDto {

    @Schema(description = "캡을 초과한 메시지 타입", example = "COMPLAINT_REPLY")
    private final AiMessageType type;

    @Schema(description = "타입별 초안 보관 개수 상한", example = "20")
    private final int limit;

    @Schema(description = "현재 보관 중인 DRAFT 개수", example = "20")
    private final long currentCount;

    @Schema(description = "확인 시 적용되는 처리 정책", example = "ARCHIVE_OLDEST")
    private final String deletePolicy;
}
