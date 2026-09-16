package com.eeum.eeum.application.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "이벤트/마케팅 알림 발송 요청 (관리자) — 마케팅 수신 동의자만 발송")
public class EventNoticeRequestDto {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200)
    @Schema(description = "이벤트 제목", example = "[이벤트] 7월 여름 특가!")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    @Schema(description = "이벤트 내용")
    private String content;

    @Schema(description = "딥링크 URL (선택)")
    private String linkUrl;
}
