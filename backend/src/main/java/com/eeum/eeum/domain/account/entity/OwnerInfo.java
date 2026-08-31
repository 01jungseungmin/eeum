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
     * 심사 대기 여부 — 상태 전이의 유일한 판별식이다.
     *
     * <p>두 축을 모두 봐야 한다. 상태만 보면 안 되는 이유: PENDING은 두 상태를 겸한다.
     * {@link #create}와 {@link #updateInfo}(사업자번호 변경)는 PENDING + {@code reviewRequestedAt = null}
     * ("아직 제출 안 함")을, {@link #requestReview}는 PENDING + 접수 시각("심사 대기")을 만든다.
     *
     * <p>접수 시각만 보면 안 되는 이유: {@link #reject}는 상태만 REJECTED로 바꾸고 접수 시각을
     * 남긴다. 시각만 보면 거절된 신청이 재신청 없이 그대로 승인돼 ROLE_OWNER가 부여된다.
     *
     * <p>따라서 승인·거절은 이 판별식이 true일 때만, 재신청은 false일 때만 허용한다.
     */
    public boolean isAwaitingReview() {
        return this.approvalStatus == ApprovalStatus.PENDING && this.reviewRequestedAt != null;
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