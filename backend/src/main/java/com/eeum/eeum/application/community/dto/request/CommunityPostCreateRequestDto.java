package com.eeum.eeum.application.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "커뮤니티 게시글 작성 요청")
public class CommunityPostCreateRequestDto {

    @NotNull
    @Schema(description = "카테고리 ID")
    private Long categoryId;

    @NotBlank
    @Size(max = 200)
    @Schema(description = "제목", example = "같이 운동할 분 구해요")
    private String title;

    @NotBlank
    @Schema(description = "내용")
    private String content;
}
