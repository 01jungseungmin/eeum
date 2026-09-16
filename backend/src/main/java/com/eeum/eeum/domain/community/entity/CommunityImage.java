package com.eeum.eeum.domain.community.entity;

import com.eeum.eeum.common.entity.ImageBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_image")
public class CommunityImage extends ImageBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_post_id", nullable = false)
    private CommunityPost post;

    public static CommunityImage create(CommunityPost post, String imageUrl, int displayOrder) {
        CommunityImage image = new CommunityImage();
        image.post = post;
        image.initImage(imageUrl, displayOrder, false);
        return image;
    }
}