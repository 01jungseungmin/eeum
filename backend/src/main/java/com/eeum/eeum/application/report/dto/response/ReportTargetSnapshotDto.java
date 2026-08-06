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

    private static final int PREVIEW_MAX_LENGTH = 200;

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

    @Schema(description = "신고 접수 시점의 대상 전체 본문")
    private final String content;

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

    /**
     * 현재 대상의 존재 여부와 작성자 정보는 유지하고, 관리 근거가 되는 제목/본문은
     * 신고 접수 시점에 저장한 불변 스냅샷으로 교체한다.
     */
    public static ReportTargetSnapshotDto withStoredContent(
            ReportTargetSnapshotDto current, String storedTitle, String storedContent) {
        String title = storedTitle != null ? storedTitle : current.title;
        String content = storedContent != null ? storedContent : current.content;
        return ReportTargetSnapshotDto.builder()
                .targetType(current.targetType)
                .targetId(current.targetId)
                .exists(current.exists)
                .title(title)
                .content(content)
                .contentPreview(preview(content))
                .ownerAccountId(current.ownerAccountId)
                .ownerName(current.ownerName)
                .ownerNickname(current.ownerNickname)
                .parentTitle(current.parentTitle)
                .parentId(current.parentId)
                .targetCreatedAt(current.targetCreatedAt)
                .build();
    }

    public static String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > PREVIEW_MAX_LENGTH
                ? text.substring(0, PREVIEW_MAX_LENGTH) + "…"
                : text;
    }
}
