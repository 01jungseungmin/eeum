package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AiExposureStatusRepository extends JpaRepository<AiExposureStatus, Long> {

    Optional<AiExposureStatus> findByStore_StoreId(Long storeId);

    // 사용자 노출 API — 노출 진행 중인 가게만. store를 fetch join해 목록/로그 생성 시 store별 N+1을 제거한다.
    @Query("SELECT e FROM AiExposureStatus e JOIN FETCH e.store WHERE e.active = true")
    List<AiExposureStatus> findByActiveTrue();
}
