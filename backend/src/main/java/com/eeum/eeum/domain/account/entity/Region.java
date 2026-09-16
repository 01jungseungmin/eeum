package com.eeum.eeum.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "region",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_region_code", columnNames = "region_code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "region_code", nullable = false, length = 20, unique = true)
    private String regionCode;

    @Column(name = "si_do", nullable = false, length = 50)
    private String siDo;

    @Column(name = "gun_gu", nullable = false, length = 50)
    private String gunGu;

    @Column(name = "dong", nullable = false, length = 50)
    private String dong;

    @Column(name = "radius", nullable = false)
    private int radius;

    public static Region create(String regionCode, String siDo, String gunGu, String dong, int radius) {
        Region region = new Region();
        region.regionCode = regionCode;
        region.siDo = siDo;
        region.gunGu = gunGu;
        region.dong = dong;
        region.radius = radius;
        return region;
    }

    public void updateRadius(int radius) {
        this.radius = radius;
    }

    public String getFullName() {
        return siDo + " " + gunGu + " " + dong;
    }
}