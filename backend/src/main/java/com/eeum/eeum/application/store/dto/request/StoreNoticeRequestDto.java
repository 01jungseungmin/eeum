package com.eeum.eeum.application.store.dto.request;

import com.eeum.eeum.domain.store.enums.StoreNoticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "상점 공지 등록/수정 요청")
public class StoreNoticeRequestDto {

    @Schema(description = "공지 제목", example = "5월 1일 노동절 휴무 안내")
    @NotBlank(message = "공지 제목은 필수입니다.")
    @Size(max = 100, message = "공지 제목은 100자 이하로 입력해야 합니다.")
    private String title;

    @Schema(description = "공지 내용", example = "5월 1일은 노동절 휴무 예정입니다. 양해 부탁드립니다.")
    @NotBlank(message = "공지 내용은 필수입니다.")
    private String content;

    @Schema(
            description = "공지 유형",
            example = "NORMAL",
            allowableValues = {"NORMAL", "CLOSED_TODAY", "SOLD_OUT"}
    )
    @NotNull(message = "공지 유형은 필수입니다.")
    private StoreNoticeType noticeType;

    @Schema(description = "상단 고정 여부", example = "true")
    private boolean pinned = false;
}