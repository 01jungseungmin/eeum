package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiExposureStatusRepository extends JpaRepository<AiExposureStatus, Long> {

    Optional<AiExposureStatus> findByStore_StoreId(Long storeId);

    // 사용자 노출 API — 노출 진행 중인 가게만
    java.util.List<AiExposureStatus> findByActiveTrue();
}
