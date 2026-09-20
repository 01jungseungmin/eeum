package com.eeum.eeum.domain.report.repository;

import com.eeum.eeum.domain.report.entity.QReport;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepositoryCustom {

    private static final QReport REPORT = QReport.report;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ReportReasonCount> countByReasonForStatus(ReportStatus status) {
        return queryFactory
                .select(Projections.constructor(ReportReasonCount.class, REPORT.reason, REPORT.count()))
                .from(REPORT)
                .where(REPORT.status.eq(status))
                .groupBy(REPORT.reason)
                .fetch();
    }
}
