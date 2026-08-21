package com.eeum.eeum.application.favorite.dto.response;

import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "찜 토글 응답")
public class FavoriteToggleResponseDto {

    @Schema(description = "찜 등록 여부. true=등록됨, false=해제됨", example = "true")
    private boolean favorited;

    @Schema(description = "찜 ID (등록 시에만 반환, 해제 시 null)", example = "10")
    private Long favoriteId;

    @Schema(description = "찜 대상 타입", example = "STORE")
    private FavoriteRefType refType;

    @Schema(description = "찜 대상 ID", example = "3")
    private Long refId;

    @Schema(description = "현재 찜 수. 비공개 대상(숨김 게시글·미승인 상점)의 찜을 해제한 경우 null이다 — "
            + "정확한 수를 돌려주면 공개 카운트 API를 막아둔 의미가 없어진다.",
            example = "128", nullable = true)
    private Long favoriteCount;

    @Schema(description = "찜 등록 시각 (해제 시 null)")
    private LocalDateTime createdAt;

    // 찜 등록 응답
    public static FavoriteToggleResponseDto added(Favorite favorite, long favoriteCount) {
        return FavoriteToggleResponseDto.builder()
                .favorited(true)
                .favoriteId(favorite.getFavoriteId())
                .refType(favorite.getRefType())
                .refId(favorite.getRefId())
                .favoriteCount(favoriteCount)
                .createdAt(favorite.getCreatedAt())
                .build();
    }

    // 찜 해제 응답
    // favoriteCount는 비공개 대상(숨김 글·미승인 상점) 해제 시 null이다 —
    // 정확한 수를 돌려주면 공개 카운트 API를 막아둔 의미가 없어진다.
    public static FavoriteToggleResponseDto removed(FavoriteRefType refType, Long refId, Long favoriteCount) {
        return FavoriteToggleResponseDto.builder()
                .favorited(false)
                .favoriteId(null)
                .refType(refType)
                .refId(refId)
                .favoriteCount(favoriteCount)
                .createdAt(null)
                .build();
    }
}