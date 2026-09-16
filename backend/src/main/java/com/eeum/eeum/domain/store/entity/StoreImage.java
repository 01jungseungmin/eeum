package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.ImageBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "store_image")
public class StoreImage extends ImageBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_image_id")
    private Long storeImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    public static StoreImage create(Store store, String imageUrl,
            int displayOrder, boolean isThumbnail) {
        StoreImage image = new StoreImage();
        image.store = store;
        image.initImage(imageUrl, displayOrder, isThumbnail);
        return image;
    }
}