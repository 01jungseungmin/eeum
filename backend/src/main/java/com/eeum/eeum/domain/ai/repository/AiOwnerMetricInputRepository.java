package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiOwnerMetricInputRepository extends JpaRepository<AiOwnerMetricInput, Long> {

    Optional<AiOwnerMetricInput> findByStore_StoreIdAndMetricTypeAndYearMonth(
            Long storeId, AiMetricType metricType, String yearMonth);

    List<AiOwnerMetricInput> findByStore_StoreIdAndMetricTypeAndYearMonthInOrderByYearMonthAsc(
            Long storeId, AiMetricType metricType, List<String> yearMonths);

    boolean existsByStore_StoreId(Long storeId);
}
