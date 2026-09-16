package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.StoreReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreReviewImageRepository extends JpaRepository<StoreReviewImage, Long> {

    List<StoreReviewImage> findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(
            Long storereviewId
    );

    int countByStoreReview_StorereviewId(Long storereviewId);

    boolean existsByStoreReview_StorereviewIdAndIsThumbnailTrue(Long storereviewId);

    void deleteAllByStoreReview_StorereviewId(Long storereviewId);
}