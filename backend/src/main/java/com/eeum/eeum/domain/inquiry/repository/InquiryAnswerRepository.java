package com.eeum.eeum.domain.inquiry.repository;

import com.eeum.eeum.domain.inquiry.entity.InquiryAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer, Long> {

    @EntityGraph(attributePaths = {"writer"})
    List<InquiryAnswer> findByInquiry_InquiryIdOrderByCreatedAtAsc(Long inquiryId);

    // 문의당 답변은 1건(uk_inquiry_answer_inquiry_id)이다. 재오픈된 문의에 답변을 또 달려는
    // 요청을 DB 제약에 도달하기 전에 걸러 명확한 비즈니스 에러로 돌려주기 위해 쓴다.
    boolean existsByInquiry_InquiryId(Long inquiryId);
}
