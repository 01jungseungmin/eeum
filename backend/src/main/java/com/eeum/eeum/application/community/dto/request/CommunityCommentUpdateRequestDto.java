package com.eeum.eeum.application.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "댓글/대댓글 수정 요청")
public class CommunityCommentUpdateRequestDto {

    @NotBlank
    @Schema(description = "내용")
    private String content;
}
