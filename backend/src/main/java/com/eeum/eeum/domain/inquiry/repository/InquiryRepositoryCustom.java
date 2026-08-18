package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryRepositoryCustom {

    /**
     * 관리자 문의 목록 검색.
     *
     * <p>검색 조건을 application 계층 DTO로 받지 않고 개별 파라미터로 받는다.
     * domain이 application을 참조하면 레이어 방향이 뒤집히고,
     * {@code LayerRuleTest.도메인은_상위_레이어에_의존하지_않는다} 규칙에 걸린다.
     *
     * @param status   null이면 전체 상태
     * @param category null이면 전체 카테고리
     * @param keyword  null/공백이면 미적용. 제목·본문 부분일치
     */
    Page<Inquiry> searchInquiries(
            InquiryTargetType targetType,
            InquiryStatus status,
            InquiryCategory category,
            String keyword,
            Pageable pageable
    );
}
