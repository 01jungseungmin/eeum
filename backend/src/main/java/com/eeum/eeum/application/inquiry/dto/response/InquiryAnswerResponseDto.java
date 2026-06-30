package com.eeum.eeum.application.inquiry.dto.response;

import com.eeum.eeum.domain.inquiry.entity.InquiryAnswer;
import com.eeum.eeum.domain.inquiry.enums.InquiryAnswerWriterType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "문의 답변 응답")
public class InquiryAnswerResponseDto {

    @Schema(description = "답변 ID")
    private Long answerId;

    @Schema(description = "답변자 유형")
    private InquiryAnswerWriterType writerType;

    @Schema(description = "답변자 이름")
    private String writerName;

    @Schema(description = "답변 내용")
    private String content;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    public static InquiryAnswerResponseDto from(InquiryAnswer answer) {
        return InquiryAnswerResponseDto.builder()
                .answerId(answer.getAnswerId())
                .writerType(answer.getWriterType())
                .writerName(answer.getWriter().getName())
                .content(answer.getContent())
                .createdAt(answer.getCreatedAt())
                .build();
    }
}
