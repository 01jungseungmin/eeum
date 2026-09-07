package com.eeum.eeum.domain.file.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.file.enums.FileObjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "file_object",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_file_object_key", columnNames = "object_key"),
                @UniqueConstraint(name = "uk_file_object_temporary_key", columnNames = "temporary_object_key")
        },
        indexes = @Index(name = "idx_file_object_cleanup", columnList = "status, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileObject extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_object_id")
    private Long fileObjectId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "temporary_object_key", nullable = false, length = 500)
    private String temporaryObjectKey;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FileObjectStatus status;

    // 정리 시도 횟수. 한계를 넘기면 CLEANUP_FAILED로 내려 스케줄러 대상에서 제외한다.
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static FileObject confirmed(Long accountId, String purpose, String temporaryObjectKey, String objectKey) {
        FileObject fileObject = new FileObject();
        fileObject.accountId = accountId;
        fileObject.purpose = purpose;
        fileObject.temporaryObjectKey = temporaryObjectKey;
        fileObject.objectKey = objectKey;
        fileObject.status = FileObjectStatus.CONFIRMED;
        return fileObject;
    }

    public boolean isOwnedBy(Long accountId, String purpose) {
        return this.accountId.equals(accountId) && this.purpose.equals(purpose);
    }

    public void attach() {
        if (status == FileObjectStatus.CLEANUP_PENDING || status == FileObjectStatus.CLEANUP_FAILED) {
            throw new IllegalStateException("정리 대상으로 전환된 파일은 연결할 수 없습니다");
        }
        if (status == FileObjectStatus.ATTACHED) {
            throw new IllegalStateException("이미 연결된 파일입니다");
        }
        status = FileObjectStatus.ATTACHED;
    }

    public void detach() {
        if (status == FileObjectStatus.ATTACHED) {
            status = FileObjectStatus.CLEANUP_PENDING;
        }
    }

    public boolean claimCleanup() {
        if (status != FileObjectStatus.CONFIRMED) {
            return false;
        }
        status = FileObjectStatus.CLEANUP_PENDING;
        return true;
    }

    /**
     * 정리 실패를 기록한다. 한계를 넘기면 CLEANUP_FAILED로 내려 더 이상 재시도하지 않는다.
     *
     * <p>실패해도 CLEANUP_PENDING으로 되돌리지 않는 이유는 S3 삭제만 성공했을 수 있어서다.
     * 그렇다고 무한히 재시도하면 영구 실패 건이 정리 큐 앞을 계속 차지해 뒤의 작업이 굶는다.
     *
     * @return 재시도를 포기하고 CLEANUP_FAILED로 내렸으면 true
     */
    public boolean recordCleanupFailure(int maxAttempts) {
        attemptCount++;
        if (attemptCount >= maxAttempts) {
            status = FileObjectStatus.CLEANUP_FAILED;
            return true;
        }
        return false;
    }

}
