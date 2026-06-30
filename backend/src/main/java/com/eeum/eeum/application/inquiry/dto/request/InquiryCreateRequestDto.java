package com.eeum.eeum.application.inquiry.dto.request;

import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "문의 작성 요청")
public class InquiryCreateRequestDto {

    @NotNull
    @Schema(description = "문의 대상 (STORE: 상점 문의, ADMIN: 관리자 문의)", example = "STORE")
    private InquiryTargetType targetType;

    @NotNull
    @Schema(description = "문의 카테고리", example = "ORDER")
    private InquiryCategory category;

    @Schema(description = "상점 문의 시 필수. ADMIN 문의면 null", example = "1")
    private Long storeId;

    @NotBlank
    @Size(max = 200)
    @Schema(description = "문의 제목", example = "주문 관련 문의드립니다")
    private String title;

    @NotBlank
    @Schema(description = "문의 내용")
    private String content;

    @Schema(description = "비밀글 여부", example = "false")
    private boolean secret;
}
