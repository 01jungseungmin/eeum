package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Region;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByRegionCode(String regionCode);

    Optional<Region> findByGunGuAndDong(String gunGu, String dong);

    List<Region> findAllBySiDoAndGunGu(String siDo, String gunGu);

    List<Region> findAllByDongContaining(String dong);
    @Query("""
        SELECT r
        FROM Region r
        WHERE r.siDo LIKE CONCAT('%', :keyword, '%')
           OR r.gunGu LIKE CONCAT('%', :keyword, '%')
           OR r.dong LIKE CONCAT('%', :keyword, '%')
           OR CONCAT(r.siDo, ' ', r.gunGu, ' ', r.dong) LIKE CONCAT('%', :keyword, '%')
        ORDER BY r.siDo ASC, r.gunGu ASC, r.dong ASC
    """)
    List<Region> searchByKeyword(
            @Param("keyword") String keyword,
            Pageable pageable
    );
    @Query(value = """
        SELECT
            r.region_id AS regionId,
            r.region_code AS regionCode,
            r.si_do AS siDo,
            r.gun_gu AS gunGu,
            r.dong AS dong,
            l.latitude AS latitude,
            l.longitude AS longitude,
            (
                6371 * acos(
                    least(1, greatest(-1,
                        cos(radians(:lat)) * cos(radians(l.latitude)) *
                        cos(radians(l.longitude) - radians(:lng)) +
                        sin(radians(:lat)) * sin(radians(l.latitude))
                    ))
                )
            ) AS distance
        FROM region r
        JOIN location l ON r.region_id = l.region_id
        WHERE l.latitude IS NOT NULL
          AND l.longitude IS NOT NULL
        HAVING distance <= :radiusKm
        ORDER BY distance ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<RegionNearbyProjection> findNearbyRegions(
            @Param("lat") double latitude,
            @Param("lng") double longitude,
            @Param("radiusKm") double radiusKm,
            @Param("limit") int limit
    );
    @Query("""
        select r
        from Region r
        where not exists (
            select l
            from Location l
            where l.region = r
        )
    """)
    List<Region> findRegionsWithoutLocation(Pageable pageable);

    boolean existsByRegionCode(String regionCode);
}