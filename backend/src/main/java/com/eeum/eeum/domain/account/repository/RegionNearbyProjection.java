package com.eeum.eeum.domain.account.repository;

public interface RegionNearbyProjection {

    Long getRegionId();

    String getRegionCode();

    String getSiDo();

    String getGunGu();

    String getDong();

    Double getLatitude();

    Double getLongitude();

    Double getDistance();
}