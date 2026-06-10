package com.eeum.eeum.application.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "시스템 공지 발송 요청 (관리자)")
public class SystemNoticeRequestDto {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200)
    @Schema(description = "공지 제목", example = "[공지] 서비스 점검 안내")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    @Schema(description = "공지 내용")
    private String content;

    @Schema(description = "딥링크 URL (선택)", example = "/notices/10")
    private String linkUrl;

    //null이면 전체 발송 특정 accountId 목록에만 발송할 경우 지정

    @Schema(description = "발송 대상 accountId 목록 (null=전체)")
    private List<Long> targetAccountIds;
}
