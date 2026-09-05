package com.eeum.eeum.application.used.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

/**
 * 중고 게시글 전용 이미지 등록 요청.
 * 공용 {@code ImageUploadListRequestDto}는 20장까지 허용하지만 중고 게시글은 10장이 상한이라,
 * 공용 DTO를 쓰면 스키마·문서가 실제보다 느슨하게 노출되고 11~20장 요청이 검증을 통과한 뒤
 * 서비스에서 뒤늦게 거절된다. 요청 단계에서 같은 상한으로 막는다.
 * 여기 @Size는 1회 요청의 상한이고, 기존 사진과 합한 총량 제한은 서비스가 검사한다.
 */
@Getter
@Schema(description = "중고 게시글 이미지 등록 요청")
public class UsedProductImageUploadListRequestDto {

    // 요소에 @NotNull이 없으면 {"images":[null]}이 검증을 통과해 서비스에서 NPE 500이 된다.
    @NotEmpty(message = "이미지는 1장 이상 등록해야 합니다.")
    @Size(max = 10, message = "중고 게시글 사진은 최대 10장까지 등록할 수 있습니다.")
    @Schema(description = "등록할 이미지 목록")
    private List<@NotNull @Valid UsedProductImageUploadRequestDto> images;
}
