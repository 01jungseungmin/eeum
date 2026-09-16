package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long>, InquiryRepositoryCustom {

    @EntityGraph(attributePaths = {"writer", "store"})
    Page<Inquiry> findByWriter_AccountId(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = {"writer", "store"})
    Page<Inquiry> findByStore_StoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"writer", "store"})
    Page<Inquiry> findByTargetType(InquiryTargetType targetType, Pageable pageable);

    @EntityGraph(attributePaths = {"writer", "store"})
    Optional<Inquiry> findByInquiryId(Long inquiryId);

    @EntityGraph(attributePaths = {"writer", "store"})
    Optional<Inquiry> findByInquiryIdAndStore_StoreId(Long inquiryId, Long storeId);

    @EntityGraph(attributePaths = {"writer", "store"})
    List<Inquiry> findByStore_StoreIdAndStatusOrderByCreatedAtDesc(Long storeId, InquiryStatus status);

    long countByStore_StoreIdAndStatus(Long storeId, InquiryStatus status);

    // AI 매니저 — 최근 기간 문의 조회 (안전 키워드 감지용, 상태 무관)
    List<Inquiry> findByStore_StoreIdAndCreatedAtAfter(Long storeId, LocalDateTime after);

    // 대시보드 요약 — 미답변 관리자 문의 건수
    long countByTargetTypeAndStatus(InquiryTargetType targetType, InquiryStatus status);
}
