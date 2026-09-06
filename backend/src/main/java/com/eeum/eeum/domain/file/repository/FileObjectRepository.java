package com.eeum.eeum.domain.file.repository;

import com.eeum.eeum.domain.file.entity.FileObject;
import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    Optional<FileObject> findByObjectKey(String objectKey);

    Optional<FileObject> findByTemporaryObjectKey(String temporaryObjectKey);

    List<FileObject> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            Collection<FileObjectStatus> statuses,
            LocalDateTime threshold
    );
}
