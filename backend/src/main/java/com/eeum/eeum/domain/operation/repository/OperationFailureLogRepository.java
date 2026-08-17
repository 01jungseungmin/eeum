package com.eeum.eeum.domain.operation.repository;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OperationFailureLogRepository extends JpaRepository<OperationFailureLog, Long> {

    // 보존 기간 경과분 물리 삭제 — Soft Delete 대상이 아니다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OperationFailureLog l where l.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
