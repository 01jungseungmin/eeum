package com.eeum.eeum.application.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "문의 답변 작성 요청")
public class InquiryAnswerCreateRequestDto {

    @NotBlank
    @Schema(description = "답변 내용")
    private String content;
}
