package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    @EntityGraph(attributePaths = {"writer", "store"})
    Page<Inquiry> findByWriter_AccountId(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = {"writer", "store"})
    Page<Inquiry> findByStore_StoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"writer", "store"})
    Page<Inquiry> findByTargetType(InquiryTargetType targetType, Pageable pageable);

    @EntityGraph(attributePaths = {"writer", "store"})
    Optional<Inquiry> findByInquiryId(Long inquiryId);
}
