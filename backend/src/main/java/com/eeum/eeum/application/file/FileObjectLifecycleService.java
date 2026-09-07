package com.eeum.eeum.application.file;

import com.eeum.eeum.domain.file.entity.FileObject;
import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import com.eeum.eeum.domain.file.repository.FileObjectRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FileObjectLifecycleService {

    private final FileObjectRepository fileObjectRepository;

    @Transactional
    public void registerConfirmed(
            Long accountId,
            FileUploadPurpose purpose,
            String temporaryObjectKey,
            String objectKey
    ) {
        fileObjectRepository.saveAndFlush(
                FileObject.confirmed(accountId, purpose.name(), temporaryObjectKey, objectKey));
    }

    @Transactional(readOnly = true)
    public Optional<String> findReusableConfirmedObjectKey(
            Long accountId,
            FileUploadPurpose purpose,
            String temporaryObjectKey
    ) {
        return fileObjectRepository.findByTemporaryObjectKey(temporaryObjectKey)
                .filter(fileObject -> fileObject.isOwnedBy(accountId, purpose.name()))
                .filter(fileObject -> fileObject.getStatus() != FileObjectStatus.CLEANUP_PENDING
                        && fileObject.getStatus() != FileObjectStatus.CLEANUP_FAILED)
                .map(FileObject::getObjectKey);
    }

    @Transactional
    public void attach(Long accountId, FileUploadPurpose purpose, String objectKey) {
        FileObject fileObject = fileObjectRepository.findForUpdateByObjectKey(objectKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        if (!fileObject.isOwnedBy(accountId, purpose.name())) {
            throw new ForbiddenException(ErrorCode.FILE_ACCESS_DENIED);
        }
        if (fileObject.getStatus() == FileObjectStatus.CLEANUP_PENDING
                || fileObject.getStatus() == FileObjectStatus.CLEANUP_FAILED) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (fileObject.getStatus() == FileObjectStatus.ATTACHED) {
            throw new ConflictException(ErrorCode.FILE_ALREADY_ATTACHED);
        }
        fileObject.attach();
    }

    @Transactional
    public void detach(String objectKey) {
        fileObjectRepository.findForUpdateByObjectKey(objectKey)
                .ifPresent(FileObject::detach);
    }

    // 여러 key를 한 번의 잠금 조회로 처리한다. key마다 detach를 부르면 이미지 수만큼
    // SELECT ... FOR UPDATE가 나가고, 잠금 순서도 호출 순서(표시 순서)에 끌려간다.
    @Transactional
    public void detachAll(Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        fileObjectRepository.findForUpdateByObjectKeyInOrderByFileObjectIdAsc(objectKeys)
                .forEach(FileObject::detach);
    }

    @Transactional
    public List<FileObjectCleanupTarget> claimExpiredUnattached(LocalDateTime threshold) {
        List<FileObject> cleanupPending = fileObjectRepository.findTop100ByStatusOrderByCreatedAtAsc(
                FileObjectStatus.CLEANUP_PENDING);
        List<FileObject> expiredConfirmed = fileObjectRepository.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(FileObjectStatus.CONFIRMED), threshold);

        return java.util.stream.Stream.concat(cleanupPending.stream(), expiredConfirmed.stream())
                .filter(fileObject -> fileObject.getStatus() == FileObjectStatus.CLEANUP_PENDING || fileObject.claimCleanup())
                .map(fileObject -> new FileObjectCleanupTarget(fileObject.getFileObjectId(), fileObject.getObjectKey()))
                .toList();
    }

    @Transactional
    public void completeCleanup(Long fileObjectId) {
        fileObjectRepository.deleteById(fileObjectId);
    }

    /**
     * 정리 실패를 기록한다. 한계를 넘긴 행은 CLEANUP_FAILED가 되어 이후 회차의
     * {@link #claimExpiredUnattached}가 집지 않는다.
     *
     * @return 재시도를 포기했으면 true
     */
    @Transactional
    public boolean recordCleanupFailure(Long fileObjectId, int maxAttempts) {
        return fileObjectRepository.findById(fileObjectId)
                .map(fileObject -> fileObject.recordCleanupFailure(maxAttempts))
                .orElse(false);
    }

    public record FileObjectCleanupTarget(Long fileObjectId, String objectKey) {
    }
}
