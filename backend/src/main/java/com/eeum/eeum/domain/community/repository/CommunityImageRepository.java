package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunityImageRepository extends JpaRepository<CommunityImage, Long> {

    List<CommunityImage> findByPost_PostIdOrderByDisplayOrder(Long postId);

    @EntityGraph(attributePaths = "post")
    @Query("SELECT ci FROM CommunityImage ci WHERE ci.post.postId IN :postIds AND ci.thumbnail = true")
    List<CommunityImage> findThumbnailsByPostIds(@Param("postIds") List<Long> postIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CommunityImage ci WHERE ci.post.postId = :postId")
    void deleteByPost_PostId(@Param("postId") Long postId);
}
