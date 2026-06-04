package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.StoreReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreReviewReplyRepository extends JpaRepository<StoreReviewReply, Long> {

    Optional<StoreReviewReply> findByStoreReview_StorereviewId(Long storereviewId);

    boolean existsByStoreReview_StorereviewId(Long storereviewId);

    void deleteByStoreReview_StorereviewId(Long storereviewId);
}