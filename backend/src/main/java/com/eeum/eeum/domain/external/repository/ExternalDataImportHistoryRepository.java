package com.eeum.eeum.domain.external.repository;

import com.eeum.eeum.domain.external.entity.ExternalDataImportHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalDataImportHistoryRepository extends JpaRepository<ExternalDataImportHistory, Long> {

    Page<ExternalDataImportHistory> findAllByOrderByImportedAtDesc(Pageable pageable);
}
