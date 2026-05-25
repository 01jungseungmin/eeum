package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.common.entity.ImageBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_image")
public class ProductImage extends ImageBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_image_id")
    private Long productImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    public static ProductImage create(Product product, String imageUrl,
            int displayOrder, boolean isThumbnail) {
        ProductImage image = new ProductImage();
        image.product = product;
        image.initImage(imageUrl, displayOrder, isThumbnail);
        return image;
    }
}