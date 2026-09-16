package com.eeum.eeum.application.inquiry.dto.response;

import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "문의 상세 응답 (본문 + 답변 목록 포함)")
public class InquiryDetailResponseDto {

    @Schema(description = "문의 ID")
    private Long inquiryId;

    @Schema(description = "제목")
    private String title;

    @Schema(description = "본문")
    private String content;

    @Schema(description = "문의 대상")
    private InquiryTargetType targetType;

    @Schema(description = "카테고리")
    private InquiryCategory category;

    @Schema(description = "처리 상태")
    private InquiryStatus status;

    @Schema(description = "비밀글 여부")
    private boolean secret;

    @Schema(description = "작성자 ID")
    private Long writerId;

    @Schema(description = "작성자 이름")
    private String writerName;

    @Schema(description = "상점 ID (STORE 문의)")
    private Long storeId;

    @Schema(description = "상점명 (STORE 문의)")
    private String storeName;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시")
    private LocalDateTime updatedAt;

    @Schema(description = "답변 목록")
    private List<InquiryAnswerResponseDto> answers;

    public static InquiryDetailResponseDto of(Inquiry inquiry, List<InquiryAnswerResponseDto> answers) {
        return InquiryDetailResponseDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .targetType(inquiry.getTargetType())
                .category(inquiry.getCategory())
                .status(inquiry.getStatus())
                .secret(inquiry.isSecret())
                .writerId(inquiry.getWriter().getAccountId())
                .writerName(inquiry.getWriter().getName())
                .storeId(inquiry.getStore() != null ? inquiry.getStore().getStoreId() : null)
                .storeName(inquiry.getStore() != null ? inquiry.getStore().getName() : null)
                .createdAt(inquiry.getCreatedAt())
                .updatedAt(inquiry.getModifiedAt())
                .answers(answers)
                .build();
    }
}
