package com.eeum.eeum.application.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "반복 불만 대응 문구 생성 요청")
public class AiComplaintDraftRequestDto {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "대응할 불만 키워드", example = "포장 지연")
    private String keyword;

    @Schema(description = "초안 보관 개수 캡 초과 시 가장 오래된 초안을 삭제하고 진행할지 여부", example = "false")
    private boolean confirmDelete;
}
