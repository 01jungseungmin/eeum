package com.eeum.eeum.domain.file.repository;

import com.eeum.eeum.domain.file.entity.FileObject;
import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    Optional<FileObject> findByObjectKey(String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FileObject> findForUpdateByObjectKey(String objectKey);

    // 여러 key를 한 번에 잠근다. key마다 조회하면 이미지 수만큼 SELECT ... FOR UPDATE가 나간다.
    // PK 오름차순으로 읽어 잠금 순서를 프로젝트 규약(ID 오름차순)에 맞춘다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FileObject> findForUpdateByObjectKeyInOrderByFileObjectIdAsc(Collection<String> objectKeys);

    Optional<FileObject> findByTemporaryObjectKey(String temporaryObjectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FileObject> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            Collection<FileObjectStatus> statuses,
            LocalDateTime threshold
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FileObject> findTop100ByStatusOrderByCreatedAtAsc(FileObjectStatus status);
}
