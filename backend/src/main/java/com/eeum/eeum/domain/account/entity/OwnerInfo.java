package com.eeum.eeum.domain.account.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
        return ownerInfo;
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
        }
    }
}