package com.eeum.eeum.application.ai.dto.request;

import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "마케팅/공지 문구 초안 생성 요청")
public class AiMarketingDraftRequestDto {

    @NotNull
    @Schema(description = "공지 유형", example = "EVENT")
    private AiNoticeType noticeType;

    @NotNull
    @Schema(description = "톤", example = "FRIENDLY")
    private AiTone tone;

    @NotEmpty
    @Schema(description = "채널 목록", example = "[\"APP_PUSH\", \"KAKAO_ALERT\"]")
    private List<AiChannel> channels;

    @Schema(description = "문구에 반영할 키워드 (예: 상품명)", example = "여름 냉면")
    private String keyword;
}
