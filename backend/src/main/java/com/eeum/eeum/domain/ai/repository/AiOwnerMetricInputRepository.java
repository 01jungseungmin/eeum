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

    // 절감 계획/운영 위험 카드 계산에 필요한 여러 지표를 한 번에 조회 (AiOwnerMetricSnapshot 구성용)
    List<AiOwnerMetricInput> findByStore_StoreIdAndMetricTypeInAndYearMonthIn(
            Long storeId, List<AiMetricType> metricTypes, List<String> yearMonths);

    boolean existsByStore_StoreId(Long storeId);
}
