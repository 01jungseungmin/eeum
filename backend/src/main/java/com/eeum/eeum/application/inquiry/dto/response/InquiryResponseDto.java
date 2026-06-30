package com.eeum.eeum.application.inquiry.dto.response;

import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "문의 목록 응답")
public class InquiryResponseDto {

    @Schema(description = "문의 ID")
    private Long inquiryId;

    @Schema(description = "제목")
    private String title;

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

    public static InquiryResponseDto from(Inquiry inquiry) {
        return InquiryResponseDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .title(inquiry.getTitle())
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
                .build();
    }
}
