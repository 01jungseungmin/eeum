package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.ImageBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "store_review_image")
public class StoreReviewImage extends ImageBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_review_image_id")
    private Long storereviewimageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_review_id", nullable = false)
    private StoreReview storeReview;

    // ===================== 정적 팩토리 메서드 =====================

    public static StoreReviewImage create(
            StoreReview storeReview,
            String imageUrl,
            int displayOrder,
            boolean isThumbnail
    ) {
        StoreReviewImage image = new StoreReviewImage();
        image.storeReview = storeReview;
        image.initImage(imageUrl, displayOrder, isThumbnail);
        return image;
    }
}