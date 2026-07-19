package com.eeum.eeum.domain.reservation.repository;

// 수용 인원별 활성 테이블 개수 집계 결과 (테이블 구성 요약용)
public interface CapacityCountProjection {

    Integer getCapacity();

    Long getCount();
}
