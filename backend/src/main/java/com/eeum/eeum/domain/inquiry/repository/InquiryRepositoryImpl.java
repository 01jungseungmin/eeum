package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.entity.QInquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class InquiryRepositoryImpl implements InquiryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Inquiry> searchInquiries(
            InquiryTargetType targetType,
            InquiryStatus status,
            InquiryCategory category,
            String keyword,
            Pageable pageable
    ) {
        QInquiry inquiry = QInquiry.inquiry;

        BooleanBuilder condition = new BooleanBuilder()
                .and(inquiry.targetType.eq(targetType))
                .and(statusEq(inquiry, status))
                .and(categoryEq(inquiry, category))
                .and(keywordContains(inquiry, keyword));

        List<Inquiry> content = queryFactory
                .selectFrom(inquiry)
                .where(condition)
                .orderBy(inquiry.createdAt.desc(), inquiry.inquiryId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 건수는 별도 카운트 쿼리로 구한다 — content 크기로 대체하면 마지막 페이지에서 총계가 어긋난다.
        Long total = queryFactory
                .select(inquiry.count())
                .from(inquiry)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private BooleanExpression statusEq(QInquiry inquiry, InquiryStatus status) {
        return status == null ? null : inquiry.status.eq(status);
    }

    private BooleanExpression categoryEq(QInquiry inquiry, InquiryCategory category) {
        return category == null ? null : inquiry.category.eq(category);
    }

    private BooleanExpression keywordContains(QInquiry inquiry, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return inquiry.title.contains(trimmed).or(inquiry.content.contains(trimmed));
    }
}
