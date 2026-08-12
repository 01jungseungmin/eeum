package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.account.dto.request.UpdateInfoRequestDto;
import com.eeum.eeum.application.account.service.AccountService;
import com.eeum.eeum.application.community.dto.request.CommunityCommentCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityCommentUpdateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityPostUpdateRequestDto;
import com.eeum.eeum.application.community.service.CommunityCommentService;
import com.eeum.eeum.application.community.service.CommunityLikeService;
import com.eeum.eeum.application.community.service.CommunityPostService;
import com.eeum.eeum.application.report.dto.request.ReportProcessRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreReviewReplyRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreReviewUpdateRequestDto;
import com.eeum.eeum.application.store.service.StoreReviewService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewImageRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 신고 제재와 사용자 쓰기 작업의 경쟁을 실제 MySQL 행 잠금/FK 조건에서 검증한다.
 * 테스트에는 @Transactional을 적용하지 않고, 각 서비스 트랜잭션 종료 후 Repository로 재조회한다.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReportActionConcurrencyIntegrationTest {

    private static final Long ADMIN_ID = 900L;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("eeum")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private final AdminReportService adminReportService;
    private final AccountService accountService;
    private final CommunityCommentService communityCommentService;
    private final CommunityLikeService communityLikeService;
    private final CommunityPostService communityPostService;
    private final StoreReviewService storeReviewService;
    private final ReportRepository reportRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final StoreReviewImageRepository reviewImageRepository;
    private final StoreReviewReplyRepository reviewReplyRepository;
    private final StoreReviewRepository reviewRepository;
    private final StoreRepository storeRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;
    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final PlatformTransactionManager transactionManager;

    private Account reporter;
    private Account author;
    private Account owner;
    private Category category;
    private CommunityPost post;
    private CommunityComment comment;
    private Store store;
    private StoreReview review;

    @BeforeEach
    void setUp() {
        reporter = accountRepository.save(Account.createUser(
                "reporter-concurrency@test.com", "encoded", "신고자", "신고자동시성", "010-1000-0001"));
        author = accountRepository.save(Account.createUser(
                "author-concurrency@test.com", "encoded", "작성자", "작성자동시성", "010-1000-0002"));
        owner = accountRepository.save(Account.createOwner(
                "owner-concurrency@test.com", "encoded", "점주", "010-1000-0003"));

        Region region = regionRepository.save(
                Region.create("REPORT-CONCURRENCY", "서울특별시", "강남구", "역삼동", 3));
        AccountRegion authorRegion = AccountRegion.create(author, region);
        authorRegion.verify();
        authorRegion = accountRegionRepository.saveAndFlush(authorRegion);
        author.setPrimaryRegion(authorRegion.getAccountRegionId());
        author = accountRepository.saveAndFlush(author);

        category = categoryRepository.save(
                Category.createRoot(CategoryType.COMMUNITY, "신고 동시성", 1));
        post = postRepository.saveAndFlush(
                CommunityPost.create(author, category, region, "동시성 게시글", "본문"));
        comment = commentRepository.saveAndFlush(
                CommunityComment.createComment(post, author, "기존 댓글"));

        store = storeRepository.saveAndFlush(
                Store.createForOwnerSignup(owner, "동시성 상점", "서울시", "02-1000-0000"));
        review = reviewRepository.saveAndFlush(
                StoreReview.createForReservation(store, author, null, 3, "기존 리뷰"));
    }

    @AfterEach
    void tearDown() {
        reportRepository.deleteAll();
        reviewImageRepository.deleteAll();
        reviewReplyRepository.deleteAll();
        reviewRepository.deleteAll();
        commentLikeRepository.deleteAll();
        postLikeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        storeRepository.deleteAll();
        accountRegionRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();

        // AFTER_COMMIT 비동기 알림이 notification=0 확인 직후 삽입될 수 있으므로
        // 알림 삭제와 계정 삭제를 함께 재시도해 FK 경쟁을 흡수한다.
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    notificationRepository.deleteAll();
                    accountRepository.deleteAll();
                    assertThat(notificationRepository.count()).isZero();
                    assertThat(accountRepository.count()).isZero();
                });
    }

    @Test
    void 동일_신고를_동시에_처리하면_정확히_한_요청만_성공한다() throws InterruptedException {
        // given
        Report report = saveReport(ReportTargetType.ACCOUNT, author.getAccountId(), author.getAccountId());
        ReportProcessRequestDto requestA = processRequest(ReportAction.DISMISS, "중복 처리 A");
        ReportProcessRequestDto requestB = processRequest(ReportAction.DISMISS, "중복 처리 B");

        // when
        RaceOutcome outcome = race(
                () -> adminReportService.processReport(report.getReportId(), ADMIN_ID, requestA),
                () -> adminReportService.processReport(report.getReportId(), ADMIN_ID + 1, requestB)
        );

        // then
        assertThat(outcome.successCount()).isEqualTo(1);
        assertThat(outcome.failure()).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) outcome.failure()).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_PROCESSED);

        Report stored = reportRepository.findById(report.getReportId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(stored.getAction()).isEqualTo(ReportAction.DISMISS);
    }

    @Test
    void 회원정보_수정과_신고_계정정지가_경쟁해도_최종_계정은_정지된다() throws InterruptedException {
        // given
        Report report = saveReport(ReportTargetType.ACCOUNT, author.getAccountId(), author.getAccountId());
        UpdateInfoRequestDto updateRequest = new UpdateInfoRequestDto();
        ReflectionTestUtils.setField(updateRequest, "nickname", "수정된작성자");
        ReportProcessRequestDto suspendRequest = processRequest(ReportAction.SUSPEND_AUTHOR, "계정 정지");

        // when
        RaceOutcome outcome = race(
                () -> accountService.updateMyInfo(author.getAccountId(), updateRequest),
                () -> adminReportService.processReport(report.getReportId(), ADMIN_ID, suspendRequest)
        );

        // then: 정지가 먼저 끝난 경우 정보 수정은 ACCOUNT_SUSPENDED로 거부될 수 있다.
        assertThat(outcome.secondError())
                .as("관리자 계정 정지는 반드시 성공해야 한다")
                .isNull();
        if (outcome.firstError() != null) {
            assertThat(outcome.firstError())
                    .as("정지가 먼저 반영되면 사용자 수정은 상태 검증 또는 optimistic lock으로 거부되어야 한다")
                    .satisfiesAnyOf(
                            error -> assertThat(error).isInstanceOf(OptimisticLockingFailureException.class),
                            error -> {
                                assertThat(error).isInstanceOf(BusinessException.class);
                                assertThat(((BusinessException) error).getErrorCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
                            }
                    );
        }
        Account stored = accountRepository.findById(author.getAccountId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(reportRepository.findById(report.getReportId()).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.REVIEWED);
    }

    @Test
    void 댓글_수정과_관리자_삭제가_경쟁해도_삭제_상태가_되돌아가지_않는다() throws InterruptedException {
        // given
        Report report = saveReport(
                ReportTargetType.COMMUNITY_COMMENT, comment.getCommentId(), author.getAccountId());
        CommunityCommentUpdateRequestDto updateRequest = new CommunityCommentUpdateRequestDto();
        ReflectionTestUtils.setField(updateRequest, "content", "동시에 수정된 댓글");
        ReportProcessRequestDto deleteRequest = processRequest(ReportAction.DELETE_COMMENT, "댓글 삭제");

        // when
        RaceOutcome outcome = race(
                () -> communityCommentService.updateComment(author.getAccountId(), comment.getCommentId(), updateRequest),
                () -> adminReportService.processReport(report.getReportId(), ADMIN_ID, deleteRequest)
        );

        // then: 삭제가 먼저 끝난 경우 사용자 수정은 대상 없음으로 거부될 수 있다.
        assertOnlyExpectedFailure(outcome, ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        CommunityComment stored = commentRepository.findById(comment.getCommentId()).orElseThrow();
        assertThat(stored.isDeleted()).isTrue();
        assertThat(stored.getContent()).isEqualTo("삭제된 댓글입니다.");
    }

    @Test
    void 게시글_삭제와_댓글_생성이_경쟁해도_고아_댓글이_남지_않는다() throws InterruptedException {
        // given
        Report report = saveReport(ReportTargetType.COMMUNITY_POST, post.getPostId(), author.getAccountId());
        CommunityCommentCreateRequestDto createRequest = new CommunityCommentCreateRequestDto();
        ReflectionTestUtils.setField(createRequest, "content", "동시에 생성한 댓글");
        ReportProcessRequestDto deleteRequest = processRequest(ReportAction.DELETE_POST, "게시글 삭제");

        // when
        RaceOutcome outcome = race(
                () -> communityCommentService.createComment(author.getAccountId(), post.getPostId(), createRequest),
                () -> adminReportService.processReport(report.getReportId(), ADMIN_ID, deleteRequest)
        );

        // then: 게시글 삭제가 먼저 끝난 경우 댓글 생성은 대상 없음으로 거부될 수 있다.
        assertOnlyExpectedFailure(outcome, ErrorCode.COMMUNITY_POST_NOT_FOUND);
        assertThat(postRepository.findById(post.getPostId())).isEmpty();
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void 게시글_삭제와_댓글_삭제가_경쟁해도_게시글과_댓글이_모두_정리된다() throws InterruptedException {
        // given
        Report report = saveReport(ReportTargetType.COMMUNITY_POST, post.getPostId(), author.getAccountId());
        ReportProcessRequestDto deleteRequest = processRequest(ReportAction.DELETE_POST, "게시글 삭제");

        // when
        RaceOutcome outcome = race(
                () -> communityCommentService.deleteComment(author.getAccountId(), comment.getCommentId()),
                () -> adminReportService.processReport(report.getReportId(), ADMIN_ID, deleteRequest)
        );

        // then: 게시글 삭제가 먼저 끝난 경우 댓글 삭제는 게시글 없음으로 거부될 수 있다.
        assertOnlyExpectedFailure(outcome, ErrorCode.COMMUNITY_POST_NOT_FOUND);
        assertThat(postRepository.findById(post.getPostId())).isEmpty();
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void 리뷰_수정과_관리자_삭제가_경쟁해도_리뷰는_삭제되고_평점이_재계산된다() throws InterruptedException {
        // given
        Report report = saveReport(ReportTargetType.STORE_REVIEW, review.getStorereviewId(), author.getAccountId());
        StoreReviewUpdateRequestDto updateRequest = new StoreReviewUpdateRequestDto();
        ReflectionTestUtils.setField(updateRequest, "rating", 5);
        ReflectionTestUtils.setField(updateRequest, "content", "동시에 수정된 리뷰");
        ReportProcessRequestDto deleteRequest = processRequest(ReportAction.DELETE_STORE_REVIEW, "리뷰 삭제");

        // when
        RaceOutcome outcome = race(
                () -> storeReviewService.updateReview(
                        author.getAccountId(), store.getStoreId(), review.getStorereviewId(), updateRequest),
                () -> adminReportService.processReport(report.getReportId(), ADMIN_ID, deleteRequest)
        );

        // then: 관리자 삭제가 먼저 끝난 경우 사용자 수정은 리뷰 없음으로 거부될 수 있다.
        assertOnlyExpectedFailure(outcome, ErrorCode.STORE_REVIEW_NOT_FOUND);
        assertThat(reviewRepository.findById(review.getStorereviewId())).isEmpty();
        Store storedStore = storeRepository.findById(store.getStoreId()).orElseThrow();
        assertThat(storedStore.getReviewCount()).isZero();
        assertThat(storedStore.getRating()).isZero();
    }

    @Test
    void 게시글_숨김_잠금을_기다린_수정은_숨김_커밋_후_거부된다() throws InterruptedException {
        CommunityPostUpdateRequestDto request = new CommunityPostUpdateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", category.getCategoryId());
        ReflectionTestUtils.setField(request, "title", "숨김 중 수정");
        ReflectionTestUtils.setField(request, "content", "수정 본문");

        Throwable error = contendWithHeldTransaction(
                this::hidePostUnderLock,
                () -> communityPostService.updatePost(author.getAccountId(), post.getPostId(), request)
        );

        assertBusinessError(error, ErrorCode.COMMUNITY_POST_NOT_FOUND);
        CommunityPost stored = postRepository.findWithAccountByPostId(post.getPostId()).orElseThrow();
        assertThat(stored.isHidden()).isTrue();
        assertThat(stored.getTitle()).isEqualTo("동시성 게시글");
    }

    @Test
    void 게시글_숨김_잠금을_기다린_댓글_생성은_숨김_커밋_후_거부된다() throws InterruptedException {
        CommunityCommentCreateRequestDto request = new CommunityCommentCreateRequestDto();
        ReflectionTestUtils.setField(request, "content", "숨김 중 댓글");

        Throwable error = contendWithHeldTransaction(
                this::hidePostUnderLock,
                () -> communityCommentService.createComment(author.getAccountId(), post.getPostId(), request)
        );

        assertBusinessError(error, ErrorCode.COMMUNITY_POST_NOT_FOUND);
        assertThat(commentRepository.count()).isEqualTo(1L);
    }

    @Test
    void 게시글_숨김_잠금을_기다린_좋아요는_숨김_커밋_후_거부된다() throws InterruptedException {
        Throwable error = contendWithHeldTransaction(
                this::hidePostUnderLock,
                () -> communityLikeService.likePost(author.getAccountId(), post.getPostId())
        );

        assertBusinessError(error, ErrorCode.COMMUNITY_POST_NOT_FOUND);
        assertThat(postLikeRepository.count()).isZero();
    }

    @Test
    void 댓글_삭제_잠금을_기다린_좋아요는_삭제_커밋_후_거부된다() throws InterruptedException {
        Throwable error = contendWithHeldTransaction(
                this::softDeleteCommentUnderLocks,
                () -> communityLikeService.likeComment(author.getAccountId(), comment.getCommentId())
        );

        assertBusinessError(error, ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        assertThat(commentLikeRepository.count()).isZero();
        CommunityComment stored = commentRepository.findById(comment.getCommentId()).orElseThrow();
        assertThat(stored.isDeleted()).isTrue();
    }

    @Test
    void 리뷰_삭제_잠금을_기다린_답글_생성은_삭제_커밋_후_대상없음으로_거부된다() throws InterruptedException {
        StoreReviewReplyRequestDto request = new StoreReviewReplyRequestDto();
        ReflectionTestUtils.setField(request, "content", "삭제 경쟁 답글");

        Throwable error = contendWithHeldTransaction(
                this::deleteReviewUnderLocks,
                () -> storeReviewService.createReply(
                        owner.getAccountId(), store.getStoreId(), review.getStorereviewId(), request
                )
        );

        assertBusinessError(error, ErrorCode.STORE_REVIEW_NOT_FOUND);
        assertThat(reviewReplyRepository.count()).isZero();
    }

    @Test
    void 리뷰_삭제_잠금을_기다린_이미지_추가는_삭제_커밋_후_대상없음으로_거부된다() throws InterruptedException {
        Throwable error = contendWithHeldTransaction(
                this::deleteReviewUnderLocks,
                () -> storeReviewService.addReviewImages(
                        author.getAccountId(),
                        store.getStoreId(),
                        review.getStorereviewId(),
                        java.util.List.of("https://example.com/race.jpg")
                )
        );

        assertBusinessError(error, ErrorCode.STORE_REVIEW_NOT_FOUND);
        assertThat(reviewImageRepository.count()).isZero();
    }

    private void hidePostUnderLock() {
        CommunityPost locked = postRepository.findWithAccountByPostIdForUpdate(post.getPostId())
                .orElseThrow();
        locked.hide();
        postRepository.flush();
    }

    private void softDeleteCommentUnderLocks() {
        postRepository.findWithAccountByPostIdForUpdate(post.getPostId()).orElseThrow();
        CommunityComment locked = commentRepository
                .findWithAccountByCommentIdForUpdate(comment.getCommentId())
                .orElseThrow();
        locked.softDelete();
        postRepository.decreaseCommentCount(post.getPostId());
    }

    private void deleteReviewUnderLocks() {
        storeRepository.findByIdWithPessimisticLock(store.getStoreId()).orElseThrow();
        StoreReview locked = reviewRepository
                .findWithAccountAndStoreByStorereviewIdForUpdate(review.getStorereviewId())
                .orElseThrow();
        reviewImageRepository.deleteAllByStoreReview_StorereviewId(review.getStorereviewId());
        reviewReplyRepository.deleteByStoreReview_StorereviewId(review.getStorereviewId());
        reviewRepository.delete(locked);
        reviewRepository.flush();
    }

    private Throwable contendWithHeldTransaction(
            Runnable lockedMutation,
            ThrowingRunnable contender
    ) throws InterruptedException {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch holderDone = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        CountDownLatch contenderDone = new CountDownLatch(1);
        AtomicReference<Throwable> holderError = new AtomicReference<>();
        AtomicReference<Throwable> contenderError = new AtomicReference<>();

        Thread holderThread = new Thread(() -> {
            try {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.executeWithoutResult(status -> {
                    lockedMutation.run();
                    lockAcquired.countDown();
                    awaitRelease(releaseLock);
                });
            } catch (Throwable throwable) {
                holderError.set(throwable);
                lockAcquired.countDown();
            } finally {
                holderDone.countDown();
            }
        });
        Thread contenderThread = new Thread(() -> {
            contenderStarted.countDown();
            try {
                contender.run();
            } catch (Throwable throwable) {
                contenderError.set(throwable);
            } finally {
                contenderDone.countDown();
            }
        });

        holderThread.start();
        assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(holderError.get()).isNull();
        contenderThread.start();
        assertThat(contenderStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(contenderDone.await(300, TimeUnit.MILLISECONDS))
                .as("경쟁 요청은 실제 DB 행 잠금에서 대기해야 한다")
                .isFalse();

        releaseLock.countDown();
        assertThat(holderDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(contenderDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(holderError.get()).isNull();
        return contenderError.get();
    }

    private void awaitRelease(CountDownLatch releaseLock) {
        try {
            if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 DB 잠금 해제 대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 DB 잠금 대기 중 중단", e);
        }
    }

    private void assertBusinessError(Throwable error, ErrorCode expectedCode) {
        assertThat(error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) error).getErrorCode()).isEqualTo(expectedCode);
    }

    private Report saveReport(ReportTargetType targetType, Long targetId, Long ownerAccountId) {
        return reportRepository.saveAndFlush(Report.create(
                reporter,
                targetType,
                targetId,
                ReportReason.ABUSE,
                "동시성 검증 신고",
                "대상 제목",
                "대상 본문",
                ownerAccountId
        ));
    }

    private ReportProcessRequestDto processRequest(ReportAction action, String adminNote) {
        ReportProcessRequestDto request = new ReportProcessRequestDto();
        ReflectionTestUtils.setField(request, "action", action);
        ReflectionTestUtils.setField(request, "adminNote", adminNote);
        return request;
    }

    private void assertOnlyExpectedFailure(RaceOutcome outcome, ErrorCode expectedCode) {
        assertThat(outcome.secondError())
                .as("관리자 제재는 반드시 성공해야 한다")
                .isNull();
        if (outcome.firstError() != null) {
            assertThat(outcome.firstError()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) outcome.firstError()).getErrorCode()).isEqualTo(expectedCode);
        }
    }

    private RaceOutcome race(ThrowingRunnable first, ThrowingRunnable second) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread firstThread = new Thread(() -> run(startLatch, doneLatch, first, firstError));
        Thread secondThread = new Thread(() -> run(startLatch, doneLatch, second, secondError));
        firstThread.start();
        secondThread.start();
        startLatch.countDown();

        assertThat(doneLatch.await(20, TimeUnit.SECONDS))
                .as("두 동시 요청은 20초 안에 완료되어야 한다")
                .isTrue();
        return new RaceOutcome(firstError.get(), secondError.get());
    }

    private void run(
            CountDownLatch startLatch,
            CountDownLatch doneLatch,
            ThrowingRunnable action,
            AtomicReference<Throwable> error
    ) {
        try {
            startLatch.await();
            action.run();
        } catch (Throwable throwable) {
            error.set(throwable);
        } finally {
            doneLatch.countDown();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record RaceOutcome(Throwable firstError, Throwable secondError) {
        int successCount() {
            int count = 0;
            if (firstError == null) count++;
            if (secondError == null) count++;
            return count;
        }

        Throwable failure() {
            return firstError != null ? firstError : secondError;
        }
    }
}
