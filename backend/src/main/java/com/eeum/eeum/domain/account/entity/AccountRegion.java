package com.eeum.eeum.domain.account.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "account_region",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_region", columnNames = {"account_id", "region_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRegion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_region_id")
    private Long accountRegionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public static AccountRegion create(Account account, Region region) {
        AccountRegion accountRegion = new AccountRegion();
        accountRegion.account = account;
        accountRegion.region = region;
        accountRegion.verified = false;
        accountRegion.verifiedAt = null;
        return accountRegion;
    }

    public void verify() {
        this.verified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    public boolean isVerified() {
        return this.verified;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.account != null
                && this.account.getAccountId() != null
                && this.account.getAccountId().equals(accountId);
    }

    public Long getRegionId() {
        return this.region.getRegionId();
    }
}