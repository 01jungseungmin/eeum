package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.entity.QInquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class InquiryRepositoryImpl implements InquiryRepositoryCustom {

    private static final QInquiry INQUIRY = QInquiry.inquiry;

    /**
     * 정렬 허용 필드. 화이트리스트 밖의 값은 무시한다 —
     * Pageable의 property는 클라이언트가 보내는 문자열이라 그대로 경로로 쓸 수 없다.
     */
    private static final Map<String, ComparableExpressionBase<?>> SORTABLE = Map.of(
            "createdAt", INQUIRY.createdAt,
            "modifiedAt", INQUIRY.modifiedAt,
            "status", INQUIRY.status,
            "category", INQUIRY.category,
            "inquiryId", INQUIRY.inquiryId
    );

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Inquiry> searchInquiries(
            InquiryTargetType targetType,
            InquiryStatus status,
            InquiryCategory category,
            String keyword,
            Pageable pageable
    ) {
        BooleanBuilder condition = new BooleanBuilder()
                .and(INQUIRY.targetType.eq(targetType))
                .and(statusEq(status))
                .and(categoryEq(category))
                .and(keywordContains(keyword));

        Sort appliedSort = resolveSort(pageable.getSort());

        List<Inquiry> content = queryFactory
                .selectFrom(INQUIRY)
                // InquiryResponseDto가 writer/store를 바로 읽는다. fetch join이 없으면
                // 페이지 크기만큼 추가 select가 나간다(N+1).
                .leftJoin(INQUIRY.writer).fetchJoin()
                .leftJoin(INQUIRY.store).fetchJoin()
                .where(condition)
                .orderBy(toOrderSpecifiers(appliedSort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 건수는 별도 카운트 쿼리로 구한다 — content 크기로 대체하면 마지막 페이지에서 총계가 어긋난다.
        // 카운트에는 fetch join을 걸지 않는다(집계에 불필요하고 QueryDSL이 거부한다).
        Long total = queryFactory
                .select(INQUIRY.count())
                .from(INQUIRY)
                .where(condition)
                .fetchOne();

        // 실제로 적용한 정렬을 그대로 담아 돌려준다 —
        // 무시한 정렬이 응답 메타데이터에 남으면 클라이언트가 정렬됐다고 오해한다.
        Pageable appliedPageable =
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), appliedSort);

        return new PageImpl<>(content, appliedPageable, total == null ? 0L : total);
    }

    private Sort resolveSort(Sort requested) {
        List<Sort.Order> applied = requested.stream()
                .filter(order -> SORTABLE.containsKey(order.getProperty()))
                .toList();
        return applied.isEmpty() ? DEFAULT_SORT : Sort.by(applied);
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            ComparableExpressionBase<?> path = SORTABLE.get(order.getProperty());
            if (path != null) {
                orders.add(order.isAscending() ? path.asc() : path.desc());
            }
        }
        // 동점 시 페이지 경계가 흔들리지 않도록 고유 키를 마지막에 붙인다.
        orders.add(INQUIRY.inquiryId.desc());
        return orders.toArray(new OrderSpecifier<?>[0]);
    }

    private BooleanExpression statusEq(InquiryStatus status) {
        return status == null ? null : INQUIRY.status.eq(status);
    }

    private BooleanExpression categoryEq(InquiryCategory category) {
        return category == null ? null : INQUIRY.category.eq(category);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return INQUIRY.title.contains(trimmed).or(INQUIRY.content.contains(trimmed));
    }
}
