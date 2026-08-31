package com.eeum.eeum.domain.operation.repository;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OperationFailureLogRepositoryCustom {

    /**
     * 실패 이력 검색.
     *
     * <p>검색 조건은 application DTO가 아니라 개별 파라미터로 받는다 —
     * domain이 상위 레이어를 참조하면 {@code LayerRuleTest} 규칙에 걸린다.
     *
     * @param category null이면 전체 카테고리
     * @param from     null이면 하한 없음
     * @param to       null이면 상한 없음
     * @param keyword  null/공백이면 미적용. operation·errorCode·errorMessage 부분일치
     */
    Page<OperationFailureLog> searchFailures(
            OperationFailureCategory category,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable
    );

    /**
     * 기준 시각 이후 발생한 실패를 카테고리별로 집계한다.
     * 대시보드 요약에서 카테고리마다 count 쿼리를 날리지 않기 위해 한 번에 묶는다.
     */
    Map<OperationFailureCategory, Long> countByCategorySince(LocalDateTime since);

    /** 최근 실패 N건 — 대시보드 요약의 "최근 실패" 목록용. */
    List<OperationFailureLog> findRecentFailures(int limit);
}
