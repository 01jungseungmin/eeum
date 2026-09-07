package com.eeum.eeum.application.used.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 중고 게시글 이미지 항목.
 * 공용 {@code ImageUploadRequestDto}의 {@code isThumbnail}은 중고 게시글에서 무시된다 —
 * 대표는 "사진이 없던 게시글의 첫 장"으로 서비스가 정하고, 변경은 별도 API를 쓴다.
 * 무시하는 필드를 계약에 남겨두면 클라이언트가 지정한 대로 될 것이라 기대하므로 아예 없앤다.
 */
@Getter
@Schema(description = "중고 게시글 이미지 항목")
public class UsedProductImageUploadRequestDto {

    @NotBlank(message = "이미지 objectKey는 필수입니다.")
    @Size(max = 1000, message = "이미지 URL은 1000자 이하로 입력해야 합니다.")
    @Schema(
            description = "S3 업로드 확정 API가 반환한 objectKey",
            example = "used/42/550e8400-e29b-41d4-a716-446655440000.webp"
    )
    @JsonProperty("objectKey")
    @JsonAlias("imageUrl")
    private String imageUrl;
}
