package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    // account, category, region 일괄 fetch — LazyInitializationException 방지
    @EntityGraph(attributePaths = {"account", "category", "region"})
    Optional<CommunityPost> findById(Long id);

    @EntityGraph(attributePaths = {"account", "category", "region"})
    Page<CommunityPost> findByRegion_RegionId(Long regionId, Pageable pageable);
}
