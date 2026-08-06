package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 신고 대상(Polymorphic 참조)을 targetType에 맞는 도메인에서 조회해 스냅샷으로 변환한다.
 * <p>
 * 대상 조회는 타입별 전용 fetch join 메서드를 사용해 소유자/상위 컨텍스트를 한 번에 로드하므로
 * 신고 1건당 대상 조회 쿼리는 1회다 (LAZY 연관 접근으로 인한 N+1 없음).
 * <p>
 * 신고 접수 이후 대상이 삭제될 수 있으므로 미존재는 예외가 아닌 exists=false 스냅샷으로 처리한다 —
 * 관리자는 삭제된 콘텐츠에 대한 신고도 열람/처리할 수 있어야 한다.
 */
@Component
@RequiredArgsConstructor
public class ReportTargetResolver {

    private static final int PREVIEW_MAX_LENGTH = 200;

    private final StoreRepository storeRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final AccountRepository accountRepository;

    public ReportTargetSnapshotDto resolve(ReportTargetType targetType, Long targetId) {
        return switch (targetType) {
            case STORE -> storeRepository.findWithAccountByStoreId(targetId)
                    .map(this::fromStore)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
            case STORE_REVIEW -> storeReviewRepository.findWithAccountAndStoreByStorereviewId(targetId)
                    .map(this::fromStoreReview)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
            case COMMUNITY_POST -> communityPostRepository.findWithAccountByPostId(targetId)
                    .map(this::fromCommunityPost)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
            case COMMUNITY_COMMENT -> communityCommentRepository.findWithAccountAndPostByCommentId(targetId)
                    .map(this::fromCommunityComment)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
            case ACCOUNT -> accountRepository.findById(targetId)
                    .map(this::fromAccount)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
        };
    }

    // ===================== 타입별 매핑 =====================

    private ReportTargetSnapshotDto fromStore(Store store) {
        Account owner = store.getAccount();
        return base(ReportTargetType.STORE, store.getStoreId())
                .title(store.getName())
                .contentPreview(truncate(store.getDescription()))
                .ownerAccountId(owner.getAccountId())
                .ownerName(owner.getName())
                .ownerNickname(owner.getNickname())
                .targetCreatedAt(store.getCreatedAt())
                .build();
    }

    private ReportTargetSnapshotDto fromStoreReview(StoreReview review) {
        Account author = review.getAccount();
        Store store = review.getStore();
        return base(ReportTargetType.STORE_REVIEW, review.getStorereviewId())
                .title("별점 " + review.getRating() + "점")
                .contentPreview(truncate(review.getContent()))
                .ownerAccountId(author.getAccountId())
                .ownerName(author.getName())
                .ownerNickname(author.getNickname())
                .parentTitle(store.getName())
                .parentId(store.getStoreId())
                .targetCreatedAt(review.getCreatedAt())
                .build();
    }

    private ReportTargetSnapshotDto fromCommunityPost(CommunityPost post) {
        Account author = post.getAccount();
        return base(ReportTargetType.COMMUNITY_POST, post.getPostId())
                .title(post.getTitle())
                .contentPreview(truncate(post.getContent()))
                .ownerAccountId(author.getAccountId())
                .ownerName(author.getName())
                .ownerNickname(author.getNickname())
                .targetCreatedAt(post.getCreatedAt())
                .build();
    }

    private ReportTargetSnapshotDto fromCommunityComment(CommunityComment comment) {
        Account author = comment.getAccount();
        CommunityPost post = comment.getPost();
        return base(ReportTargetType.COMMUNITY_COMMENT, comment.getCommentId())
                // 삭제된 댓글은 본문이 노출되지 않도록 하되 신고 자체는 열람 가능해야 한다
                .contentPreview(comment.isDeleted() ? null : truncate(comment.getContent()))
                .ownerAccountId(author.getAccountId())
                .ownerName(author.getName())
                .ownerNickname(author.getNickname())
                .parentTitle(post.getTitle())
                .parentId(post.getPostId())
                .targetCreatedAt(comment.getCreatedAt())
                .build();
    }

    private ReportTargetSnapshotDto fromAccount(Account account) {
        return base(ReportTargetType.ACCOUNT, account.getAccountId())
                .title(account.getName())
                .ownerAccountId(account.getAccountId())
                .ownerName(account.getName())
                .ownerNickname(account.getNickname())
                .targetCreatedAt(account.getCreatedAt())
                .build();
    }

    // ===================== 내부 유틸 =====================

    private ReportTargetSnapshotDto.ReportTargetSnapshotDtoBuilder base(
            ReportTargetType targetType, Long targetId) {
        return ReportTargetSnapshotDto.builder()
                .targetType(targetType)
                .targetId(targetId)
                .exists(true);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > PREVIEW_MAX_LENGTH
                ? text.substring(0, PREVIEW_MAX_LENGTH) + "…"
                : text;
    }
}
