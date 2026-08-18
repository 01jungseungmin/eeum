package com.eeum.eeum.domain.used.entity;

import com.eeum.eeum.common.entity.ImageBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 중고 게시글 사진
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "used_product_image",
        indexes = @Index(name = "idx_used_product_image_product", columnList = "used_product_id, display_order")
)
public class UsedProductImage extends ImageBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "used_product_image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_product_id", nullable = false)
    private UsedProduct usedProduct;

    public static UsedProductImage create(
            UsedProduct usedProduct,
            String imageUrl,
            int displayOrder,
            boolean isThumbnail
    ) {
        UsedProductImage image = new UsedProductImage();
        image.usedProduct = usedProduct;
        image.initImage(imageUrl, displayOrder, isThumbnail);
        return image;
    }

    public boolean belongsTo(Long usedProductId) {
        return this.usedProduct.getUsedProductId().equals(usedProductId);
    }
}
