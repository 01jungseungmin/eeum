package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 신고 대상(Polymorphic 참조)을 targetType에 맞는 도메인에서 조회해 스냅샷으로 변환한다.
 * 대상 조회는 타입별 전용 fetch join 메서드를 사용해 소유자/상위 컨텍스트를 한 번에 로드하므로
 * 신고 1건당 대상 조회 쿼리는 1회다 (LAZY 연관 접근으로 인한 N+1 없음).
 * 신고 접수 이후 대상이 삭제될 수 있으므로 미존재는 예외가 아닌 exists=false 스냅샷으로 처리한다 —
 * 관리자는 삭제된 콘텐츠에 대한 신고도 열람/처리할 수 있어야 한다.
 */
@Component
@RequiredArgsConstructor
public class ReportTargetResolver {

    private final StoreRepository storeRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final AccountRepository accountRepository;
    private final UsedProductRepository usedProductRepository;

    // 관리자 열람용 — 숨김 게시글도 그대로 반환한다. 관리자는 숨긴 콘텐츠의 신고도 처리해야 한다.
    public ReportTargetSnapshotDto resolve(ReportTargetType targetType, Long targetId) {
        return resolveInternal(targetType, targetId, false);
    }

    private ReportTargetSnapshotDto resolveInternal(
            ReportTargetType targetType, Long targetId, boolean forCreation) {
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
                    .filter(comment -> !comment.isDeleted())
                    .map(this::fromCommunityComment)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
            case USED_PRODUCT -> usedProductRepository.findWithSellerByUsedProductIdAndDeletedAtIsNull(targetId)
                    // 신고 접수에서는 숨김 글을 없는 것으로 취급한다. 상세 조회(UsedProductService)가
                    // 숨김 글에 404를 주는데 신고만 성공하면, ID를 넣어보는 것으로 숨김 글의 존재가 드러난다.
                    .filter(product -> !forCreation || !product.isHidden())
                    .map(this::fromUsedProduct)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
            case ACCOUNT -> accountRepository.findById(targetId)
                    .filter(account -> account.getDeletedAt() == null)
                    .map(this::fromAccount)
                    .orElseGet(() -> ReportTargetSnapshotDto.deleted(targetType, targetId));
        };
    }

    // 신고 접수 시에는 대상이 반드시 존재해야 하므로 타입별 표준 NotFound 예외로 변환한다.
    public ReportTargetSnapshotDto resolveForCreation(ReportTargetType targetType, Long targetId) {
        ReportTargetSnapshotDto target = resolveInternal(targetType, targetId, true);
        if (target.isExists()) {
            return target;
        }
        throw switch (targetType) {
            case STORE -> new NotFoundException(ErrorCode.STORE_NOT_FOUND);
            case STORE_REVIEW -> new NotFoundException(ErrorCode.STORE_REVIEW_NOT_FOUND);
            case COMMUNITY_POST -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
            case COMMUNITY_COMMENT -> new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
            case ACCOUNT -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND);
            case USED_PRODUCT -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
        };
    }

    // ===================== 타입별 매핑 =====================

    private ReportTargetSnapshotDto fromStore(Store store) {
        Account owner = store.getAccount();
        String content = store.getDescription();
        return base(ReportTargetType.STORE, store.getStoreId())
                .title(store.getName())
                .content(content)
                .contentPreview(ReportTargetSnapshotDto.preview(content))
                .ownerAccountId(owner.getAccountId())
                .ownerName(owner.getName())
                .ownerNickname(owner.getNickname())
                .targetCreatedAt(store.getCreatedAt())
                .build();
    }

    private ReportTargetSnapshotDto fromStoreReview(StoreReview review) {
        Account author = review.getAccount();
        Store store = review.getStore();
        String content = review.getContent();
        return base(ReportTargetType.STORE_REVIEW, review.getStorereviewId())
                .title("별점 " + review.getRating() + "점")
                .content(content)
                .contentPreview(ReportTargetSnapshotDto.preview(content))
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
        String content = post.getContent();
        return base(ReportTargetType.COMMUNITY_POST, post.getPostId())
                .title(post.getTitle())
                .content(content)
                .contentPreview(ReportTargetSnapshotDto.preview(content))
                .ownerAccountId(author.getAccountId())
                .ownerName(author.getName())
                .ownerNickname(author.getNickname())
                .targetCreatedAt(post.getCreatedAt())
                .build();
    }

    private ReportTargetSnapshotDto fromUsedProduct(UsedProduct product) {
        Account seller = product.getSeller();
        String content = product.getContent();
        return base(ReportTargetType.USED_PRODUCT, product.getUsedProductId())
                .title(product.getTitle())
                .content(content)
                .contentPreview(ReportTargetSnapshotDto.preview(content))
                .ownerAccountId(seller.getAccountId())
                .ownerName(seller.getName())
                .ownerNickname(seller.getNickname())
                .targetCreatedAt(product.getCreatedAt())
                .build();
    }

    private ReportTargetSnapshotDto fromCommunityComment(CommunityComment comment) {
        Account author = comment.getAccount();
        CommunityPost post = comment.getPost();
        String content = comment.getContent();
        return base(ReportTargetType.COMMUNITY_COMMENT, comment.getCommentId())
                .content(content)
                .contentPreview(ReportTargetSnapshotDto.preview(content))
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

}
