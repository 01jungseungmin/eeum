package com.eeum.eeum.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class ImageBase extends BaseEntity {

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_thumbnail", nullable = false)
    private boolean isThumbnail = false;

    protected void initImage(
            String imageUrl,
            int displayOrder,
            boolean isThumbnail
    ) {
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
        this.isThumbnail = isThumbnail;
    }

    public void markAsThumbnail() {
        this.isThumbnail = true;
    }

    public void unmarkAsThumbnail() {
        this.isThumbnail = false;
    }

    public boolean isThumbnail() {
        return isThumbnail;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

}