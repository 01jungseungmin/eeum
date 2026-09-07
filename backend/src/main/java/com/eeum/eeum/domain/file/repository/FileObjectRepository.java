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

    // 잠글 대상을 먼저 고르기만 한다. 잠금은 PK로 다시 건다 — 아래 메서드 참고.
    List<FileObject> findByObjectKeyIn(Collection<String> objectKeys);

    // 여러 행을 한 번에 잠근다. 행마다 조회하면 이미지 수만큼 SELECT ... FOR UPDATE가 나간다.
    // 조건을 PK로 두는 것이 핵심이다. InnoDB는 ORDER BY가 아니라 스캔한 인덱스 순서로 행을
    // 잠그므로, object_key IN (...)으로 잠그면 uk_file_object_key 순서(=UUID 순서)에 끌려간다.
    // PK IN이면 스캔 순서가 곧 ID 오름차순이라 프로젝트 잠금 순서 규약과 일치한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FileObject> findForUpdateByFileObjectIdInOrderByFileObjectIdAsc(Collection<Long> fileObjectIds);

    Optional<FileObject> findByTemporaryObjectKey(String temporaryObjectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FileObject> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            Collection<FileObjectStatus> statuses,
            LocalDateTime threshold
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FileObject> findTop100ByStatusOrderByCreatedAtAsc(FileObjectStatus status);
}
