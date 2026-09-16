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

    @Schema(description = "최종 수정일시. 수정된 적이 없으면 작성일시와 같다")
    private LocalDateTime modifiedAt;

    @Schema(description = "수정 여부. true이면 프론트에서 '수정됨' 표시")
    private boolean edited;

    public static InquiryAnswerResponseDto from(InquiryAnswer answer) {
        return InquiryAnswerResponseDto.builder()
                .answerId(answer.getAnswerId())
                .writerType(answer.getWriterType())
                .writerName(answer.getWriter().getName())
                .content(answer.getContent())
                .createdAt(answer.getCreatedAt())
                .modifiedAt(answer.getModifiedAt())
                .edited(isEdited(answer))
                .build();
    }

    // JPA 감사 필드는 최초 저장 시 createdAt과 modifiedAt이 같은 값으로 채워진다.
    // 둘이 달라졌다는 것은 이후 한 번이라도 수정됐다는 뜻이다.
    private static boolean isEdited(InquiryAnswer answer) {
        return answer.getModifiedAt() != null
                && answer.getCreatedAt() != null
                && answer.getModifiedAt().isAfter(answer.getCreatedAt());
    }
}
