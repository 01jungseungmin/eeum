package com.eeum.eeum.domain.community.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_image")
public class CommunityImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_post_id", nullable = false)
    private CommunityPost post;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_thumbnail", nullable = false)
    private boolean thumbnail = false;

    public static CommunityImage create(
            CommunityPost post,
            String imageUrl,
            int displayOrder,
            boolean thumbnail
    ) {
        CommunityImage image = new CommunityImage();
        image.post = post;
        image.imageUrl = imageUrl;
        image.displayOrder = displayOrder;
        image.thumbnail = thumbnail;
        return image;
    }

    public void changeThumbnail(boolean thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}