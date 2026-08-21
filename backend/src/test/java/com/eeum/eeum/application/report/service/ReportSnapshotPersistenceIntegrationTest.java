package com.eeum.eeum.application.report.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.community.service.CommunityCommentService;
import com.eeum.eeum.application.community.service.CommunityPostService;
import com.eeum.eeum.application.report.dto.request.ReportCreateRequestDto;
import com.eeum.eeum.application.report.dto.request.ReportReviewRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportResponseDto;
import com.eeum.eeum.application.report.dto.response.MyReportResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신고 접수 시 저장한 콘텐츠 스냅샷이 실제 MySQL에서도 대상 삭제와 독립적으로 보존되는지 검증한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ReportSnapshotPersistenceIntegrationTest extends IntegrationTestSupport {

    private static final String POST_TITLE = "신고 접수 당시 게시글 제목";
    private static final String POST_CONTENT = "신고 접수 당시 게시글 전체 본문";
    private static final String COMMENT_CONTENT = "신고 접수 당시 댓글 원문";


    private final ReportService reportService;
    private final AdminReportService adminReportService;
    private final CommunityPostService communityPostService;
    private final CommunityCommentService communityCommentService;
    private final ReportRepository reportRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;
    private final AccountRepository accountRepository;

    private Long reporterId;
    private Long authorId;
    private Long postId;

    @BeforeEach
    void setUp() {
        Account reporter = accountRepository.save(Account.createUser(
                "reporter@test.com", "encoded_pw", "신고자", "신고자닉", "010-1111-1111"));
        Account author = accountRepository.save(Account.createUser(
                "author@test.com", "encoded_pw", "작성자", "작성자닉", "010-2222-2222"));
        reporterId = reporter.getAccountId();
        authorId = author.getAccountId();

        Region region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.COMMUNITY, "동네 이야기", 1));
        CommunityPost post = communityPostRepository.saveAndFlush(
                CommunityPost.create(author, category, region, POST_TITLE, POST_CONTENT));
        postId = post.getPostId();
    }

    @AfterEach
    void tearDown() {
        reportRepository.deleteAll();
        communityCommentRepository.deleteAll();
        communityPostRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 게시글이_hard_delete돼도_신고_상세는_접수_시점_제목과_본문을_보존한다() {
        // given: 실제 신고 생성 트랜잭션이 게시글 제목과 본문을 report 테이블에 저장한다.
        Long reportId = reportService.createReport(
                reporterId,
                reportRequest(ReportTargetType.COMMUNITY_POST, postId))
                .getReportId();

        // when: 작성자가 실제 게시글 삭제 흐름으로 대상을 물리 삭제한다.
        communityPostService.deletePost(authorId, postId);

        // then 1: 대상 게시글은 DB에서 사라졌지만 신고 행의 스냅샷은 남아 있다.
        assertThat(communityPostRepository.findById(postId)).isEmpty();
        Report stored = reportRepository.findByReportId(reportId).orElseThrow();
        assertThat(stored.getTargetTitleSnapshot()).isEqualTo(POST_TITLE);
        assertThat(stored.getTargetContentSnapshot()).isEqualTo(POST_CONTENT);

        // then 2: 별도 관리자 상세 조회에서도 삭제 상태와 접수 당시 원문을 함께 반환한다.
        ReportResponseDto detail = adminReportService.getReportDetail(reportId);
        assertThat(detail.getTarget().isExists()).isFalse();
        assertThat(detail.getTarget().getTitle()).isEqualTo(POST_TITLE);
        assertThat(detail.getTarget().getContent()).isEqualTo(POST_CONTENT);
        assertThat(detail.getTarget().getContentPreview()).isEqualTo(POST_CONTENT);
    }

    @Test
    void 댓글이_삭제_상태가_돼도_신고_상세는_접수_시점_본문을_보존한다() {
        // given
        Account author = accountRepository.findById(authorId).orElseThrow();
        CommunityPost post = communityPostRepository.findById(postId).orElseThrow();
        Long commentId = communityCommentRepository.saveAndFlush(
                CommunityComment.createComment(post, author, COMMENT_CONTENT))
                .getCommentId();
        Long reportId = reportService.createReport(
                reporterId,
                reportRequest(ReportTargetType.COMMUNITY_COMMENT, commentId))
                .getReportId();

        // when: 댓글은 행을 유지하면서 삭제 상태와 대체 문구로 전환된다.
        communityCommentService.deleteComment(authorId, commentId);

        // then 1: 실제 댓글 행은 삭제 상태지만 신고 행에는 원문 스냅샷이 남아 있다.
        CommunityComment deleted = communityCommentRepository.findById(commentId).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getContent()).isEqualTo("삭제된 댓글입니다.");
        Report stored = reportRepository.findByReportId(reportId).orElseThrow();
        assertThat(stored.getTargetContentSnapshot()).isEqualTo(COMMENT_CONTENT);

        // then 2: 관리자 상세는 현재 삭제 대체 문구가 아니라 신고 접수 당시 원문을 반환한다.
        ReportResponseDto detail = adminReportService.getReportDetail(reportId);
        assertThat(detail.getTarget().isExists()).isFalse();
        assertThat(detail.getTarget().getContent()).isEqualTo(COMMENT_CONTENT);
        assertThat(detail.getTarget().getContentPreview()).isEqualTo(COMMENT_CONTENT);
    }

    @Test
    void 신고_검토_응답은_JPA_flush_후_갱신된_처리시각을_반환한다() throws InterruptedException {
        // given: 접수 시각과 처리 시각을 분명히 구분한다.
        MyReportResponseDto created = reportService.createReport(
                reporterId,
                reportRequest(ReportTargetType.COMMUNITY_POST, postId));
        Thread.sleep(10);
        ReportReviewRequestDto request = new ReportReviewRequestDto();
        ReflectionTestUtils.setField(request, "adminNote", "게시글 확인 완료");

        // when
        ReportResponseDto reviewed = adminReportService.reviewReport(created.getReportId(), request);

        // then 1: 즉시 응답에 flush 시점의 modifiedAt이 반영된다.
        assertThat(reviewed.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(reviewed.getProcessedAt()).isEqualTo(reviewed.getUpdatedAt());
        assertThat(reviewed.getProcessedAt()).isAfter(created.getUpdatedAt());

        // then 2: 별도 재조회한 DB 값과 응답 시각이 같다.
        Report stored = reportRepository.findByReportId(created.getReportId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        long nanosDifference = Math.abs(ChronoUnit.NANOS.between(
                stored.getModifiedAt(), reviewed.getProcessedAt()));
        assertThat(nanosDifference)
                .as("MySQL DATETIME 저장 반올림 오차는 1마이크로초 이내여야 한다")
                .isLessThanOrEqualTo(1_000L);
    }

    private ReportCreateRequestDto reportRequest(ReportTargetType targetType, Long targetId) {
        ReportCreateRequestDto request = new ReportCreateRequestDto();
        ReflectionTestUtils.setField(request, "targetType", targetType);
        ReflectionTestUtils.setField(request, "targetId", targetId);
        ReflectionTestUtils.setField(request, "reason", ReportReason.ABUSE);
        ReflectionTestUtils.setField(request, "content", "관리자 확인이 필요합니다");
        return request;
    }
}
