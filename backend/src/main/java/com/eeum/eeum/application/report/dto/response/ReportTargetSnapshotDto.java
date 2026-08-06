package com.eeum.eeum.application.report.dto.response;

import com.eeum.eeum.domain.report.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 신고 대상(Polymorphic 참조)의 스냅샷.
 * targetType에 따라 채워지는 필드가 다르며, 대상이 이미 삭제된 경우 exists=false로만 반환한다.
 */
@Getter
@Builder
@Schema(description = "신고 대상 상세 정보")
public class ReportTargetSnapshotDto {

    @Schema(description = "신고 대상 유형")
    private final ReportTargetType targetType;

    @Schema(description = "신고 대상 ID")
    private final Long targetId;

    @Schema(description = "대상 존재 여부 — false면 신고 접수 후 삭제된 콘텐츠")
    private final boolean exists;

    @Schema(description = "대상 제목 (STORE=가게명, COMMUNITY_POST=게시글 제목, ACCOUNT=이름)")
    private final String title;

    @Schema(description = "대상 본문 미리보기 (최대 200자)")
    private final String contentPreview;

    @Schema(description = "대상 작성자/소유자 accountId (ACCOUNT 타입이면 대상 본인)")
    private final Long ownerAccountId;

    @Schema(description = "대상 작성자/소유자 이름")
    private final String ownerName;

    @Schema(description = "대상 작성자/소유자 닉네임")
    private final String ownerNickname;

    @Schema(description = "상위 컨텍스트 설명 (STORE_REVIEW=가게명, COMMUNITY_COMMENT=게시글 제목)")
    private final String parentTitle;

    @Schema(description = "상위 컨텍스트 ID (STORE_REVIEW=storeId, COMMUNITY_COMMENT=postId)")
    private final Long parentId;

    @Schema(description = "대상 생성일시")
    private final LocalDateTime targetCreatedAt;

    // 대상이 이미 삭제된 경우
    public static ReportTargetSnapshotDto deleted(ReportTargetType targetType, Long targetId) {
        return ReportTargetSnapshotDto.builder()
                .targetType(targetType)
                .targetId(targetId)
                .exists(false)
                .build();
    }
}
