package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.entity.QCommunityPost;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CommunityPostRepositoryImpl implements CommunityPostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QCommunityPost post = QCommunityPost.communityPost;

    @Override
    public Page<CommunityPost> searchByRegionAndKeyword(Long regionId, String keyword, Pageable pageable) {
        return search(regionId, keyword, null, pageable);
    }

    @Override
    public Page<CommunityPost> searchByRegionKeywordAndCategoryIds(
            Long regionId,
            String keyword,
            List<Long> categoryIds,
            Pageable pageable
    ) {
        return search(regionId, keyword, categoryIds, pageable);
    }

    private Page<CommunityPost> search(
            Long regionId,
            String keyword,
            List<Long> categoryIds,
            Pageable pageable
    ) {
        List<CommunityPost> content = queryFactory
                .selectFrom(post)
                .join(post.account).fetchJoin()
                .join(post.category).fetchJoin()
                .join(post.region).fetchJoin()
                .where(
                        post.region.regionId.eq(regionId),
                        post.hidden.isFalse(),
                        keywordContains(keyword),
                        categoryIn(categoryIds)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.region.regionId.eq(regionId),
                        post.hidden.isFalse(),
                        keywordContains(keyword),
                        categoryIn(categoryIds)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression keywordContains(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : post.title.contains(keyword).or(post.content.contains(keyword));
    }

    private BooleanExpression categoryIn(List<Long> categoryIds) {
        return categoryIds == null || categoryIds.isEmpty()
                ? null
                : post.category.categoryId.in(categoryIds);
    }
}
