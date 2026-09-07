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
        if (status == FileObjectStatus.CLEANUP_PENDING) {
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

}
