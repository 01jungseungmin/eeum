package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.domain.store.enums.StoreNoticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상점 공지 응답")
public class StoreNoticeResponseDto {

    @Schema(description = "공지 ID", example = "1")
    private Long noticeId;

    @Schema(description = "공지 제목", example = "5월 1일 노동절 휴무 안내")
    private String title;

    @Schema(description = "공지 내용", example = "5월 1일은 노동절 휴무 예정입니다. 양해 부탁드립니다.")
    private String content;

    @Schema(description = "상단 고정 여부", example = "true")
    private boolean pinned;

    @Schema(description = "등록일시", example = "2026-05-25T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2026-05-25T13:00:00")
    private LocalDateTime modifiedAt;

    @Schema(description = "공지 유형", example = "NORMAL")
    private StoreNoticeType noticeType;
}