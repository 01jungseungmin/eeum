package com.eeum.eeum.application.file;

import com.eeum.eeum.domain.file.entity.FileObject;
import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import com.eeum.eeum.domain.file.repository.FileObjectRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
                .filter(fileObject -> fileObject.getStatus() != FileObjectStatus.CLEANUP_PENDING)
                .map(FileObject::getObjectKey);
    }

    @Transactional
    public void attach(Long accountId, FileUploadPurpose purpose, String objectKey) {
        FileObject fileObject = fileObjectRepository.findByObjectKey(objectKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        if (!fileObject.isOwnedBy(accountId, purpose.name())) {
            throw new ForbiddenException(ErrorCode.FILE_ACCESS_DENIED);
        }
        if (fileObject.getStatus() == FileObjectStatus.CLEANUP_PENDING) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        fileObject.attach();
    }

    @Transactional
    public List<FileObjectCleanupTarget> claimExpiredUnattached(LocalDateTime threshold) {
        return fileObjectRepository.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                        List.of(FileObjectStatus.CONFIRMED, FileObjectStatus.CLEANUP_PENDING), threshold)
                .stream()
                .filter(fileObject -> fileObject.getStatus() == FileObjectStatus.CLEANUP_PENDING
                        || fileObject.claimCleanup())
                .map(fileObject -> new FileObjectCleanupTarget(fileObject.getFileObjectId(), fileObject.getObjectKey()))
                .toList();
    }

    @Transactional
    public void completeCleanup(Long fileObjectId) {
        fileObjectRepository.deleteById(fileObjectId);
    }

    @Transactional
    public void cancelCleanup(Long fileObjectId) {
        fileObjectRepository.findById(fileObjectId).ifPresent(FileObject::cancelCleanup);
    }

    public record FileObjectCleanupTarget(Long fileObjectId, String objectKey) {
    }
}
