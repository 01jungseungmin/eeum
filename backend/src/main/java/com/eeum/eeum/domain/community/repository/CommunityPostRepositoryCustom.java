package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommunityPostRepositoryCustom {

    Page<CommunityPost> searchByRegionAndKeyword(Long regionId, String keyword, Pageable pageable);
}
