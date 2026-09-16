package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.application.account.dto.request.OwnerInfoSearchDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationListResponseDto;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OwnerInfoRepositoryCustom {

    Page<OwnerApplicationListResponseDto> searchOwnerApplications(
            OwnerInfoSearchDto condition,
            Pageable pageable
    );
}