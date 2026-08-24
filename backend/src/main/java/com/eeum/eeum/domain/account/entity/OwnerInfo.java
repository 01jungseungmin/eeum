package com.eeum.eeum.domain.account.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "owner_info",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_owner_info_account", columnNames = "account_id"),
                @UniqueConstraint(name = "uk_owner_info_business_number", columnNames = "business_number")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnerInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "owner_info_id")
    private Long ownerInfoId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(name = "business_number", nullable = false, length = 50, unique = true)
    private String businessNumber;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "review_requested_at")
    private LocalDateTime reviewRequestedAt;

    public static OwnerInfo create(
            Account account,
            String businessNumber,
            LocalDate openingDate
    ) {
        OwnerInfo ownerInfo = new OwnerInfo();
        ownerInfo.account = account;
        ownerInfo.businessNumber = businessNumber;
        ownerInfo.openingDate = openingDate;
        ownerInfo.approvalStatus = ApprovalStatus.PENDING;
        ownerInfo.rejectionReason = null;
        ownerInfo.reviewRequestedAt = null;
        return ownerInfo;
    }

    /**
     * 심사 접수 여부 — 상태 전이의 실제 판별식이다.
     *
     * <p>{@code approvalStatus == PENDING}으로 판별하면 안 된다. PENDING은 두 상태를 겸한다:
     * {@link #create}와 {@link #updateInfo}(사업자번호 변경)는 PENDING + {@code reviewRequestedAt = null}
     * ("아직 제출 안 함")을 만들고, {@link #requestReview}는 PENDING + 접수 시각("심사 대기")을 만든다.
     * 승인·거절은 후자만, 재신청은 전자만 허용해야 하므로 두 서비스가 같은 정의를 공유한다.
     */
    public boolean isReviewRequested() {
        return this.reviewRequestedAt != null;
    }

    public boolean isApproved() {
        return this.approvalStatus == ApprovalStatus.APPROVED;
    }

    public void requestReview() {
        this.approvalStatus = ApprovalStatus.PENDING;
        this.rejectionReason = null;
        this.reviewRequestedAt = LocalDateTime.now();
    }

    public void approve() {
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.rejectionReason = null;
        this.account.approveOwner();
    }

    public void reject(String reason) {
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void updateInfo(String businessNumber) {
        if (businessNumber != null && !businessNumber.equals(this.businessNumber)) {
            this.businessNumber = businessNumber;
            this.approvalStatus = ApprovalStatus.PENDING;
            this.rejectionReason = null;
            this.reviewRequestedAt = null;
        }
    }

    public boolean isBusinessVerified() {
        return this.businessNumber != null && !this.businessNumber.isBlank()
                && this.openingDate != null;
    }
}