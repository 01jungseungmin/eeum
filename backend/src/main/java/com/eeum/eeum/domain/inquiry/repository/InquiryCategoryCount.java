package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;

/** 문의 유형별 건수 집계 원값. */
public record InquiryCategoryCount(InquiryCategory category, Long count) {
}
