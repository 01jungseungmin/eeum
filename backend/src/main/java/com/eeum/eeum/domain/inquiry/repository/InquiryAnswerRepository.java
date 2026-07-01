package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.entity.InquiryAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer, Long> {

    @EntityGraph(attributePaths = {"writer"})
    List<InquiryAnswer> findByInquiry_InquiryIdOrderByCreatedAtAsc(Long inquiryId);
}
