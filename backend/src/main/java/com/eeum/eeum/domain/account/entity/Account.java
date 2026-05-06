package com.eeum.eeum.domain.account.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_account_nickname", columnNames = "nickname")
        },
        indexes = { //인덱스 primary_region_id 컬럼으로 검색할 일이 있을 때 더 빠르게 찾기 위한 설정
                @Index(name = "idx_account_primary_region", columnList = "primary_region_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    private static final String DEFAULT_PROFILE_IMAGE_URL = "/default-profile.png";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    // FK 제약은 init.sql에 없음. 서비스 레이어에서 AccountRegion 검증 후 관리.
    @Column(name = "primary_region_id")
    private Long primaryRegionId;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "nickname", length = 50, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Account createUser(
            String email,
            String encodedPassword,
            String name,
            String nickname,
            String phone
    ) {
        Account account = new Account();
        account.email = email;
        account.password = encodedPassword;
        account.name = name;
        account.nickname = nickname;
        account.phone = phone;
        account.provider = OAuthProvider.LOCAL;
        account.providerId = null;
        account.profileImageUrl = DEFAULT_PROFILE_IMAGE_URL;
        account.role = AccountRole.ROLE_USER;
        account.status = AccountStatus.ACTIVE;
        account.emailVerified = true;
        return account;
    }

    public static Account createOAuthUser(
            String email,
            String name,
            String nickname,
            String phone,
            OAuthProvider provider,
            String providerId
    ) {
        Account account = new Account();
        account.email = email;
        account.password = null;
        account.name = name;
        account.nickname = nickname;
        account.phone = phone;
        account.provider = provider;
        account.providerId = providerId;
        account.profileImageUrl = DEFAULT_PROFILE_IMAGE_URL;
        account.role = AccountRole.ROLE_USER;
        account.status = AccountStatus.ACTIVE;
        account.emailVerified = true;
        return account;
    }

    public void approveOwner() {
        this.role = AccountRole.ROLE_OWNER;
    }

    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
        this.deletedAt = null;
    }

    public void cancelWithdrawal() {
        this.status = AccountStatus.ACTIVE;
        this.deletedAt = null;
    }

    public void withdraw() {
        this.status = AccountStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateInfo(String nickname, String profileImageUrl) {
        if (nickname != null) {
            this.nickname = nickname;
        }

        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void setPrimaryRegion(Long regionId) {
        this.primaryRegionId = regionId;
    }

    public void clearPrimaryRegion() {
        this.primaryRegionId = null;
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public boolean isWithdrawn() {
        return this.status == AccountStatus.WITHDRAWN;
    }

    public boolean isAdmin() {
        return this.role == AccountRole.ROLE_ADMIN;
    }

    public boolean isOwner() {
        return this.role == AccountRole.ROLE_OWNER;
    }

    public boolean isLocalAccount() {
        return this.provider == OAuthProvider.LOCAL;
    }

    public boolean isOAuthAccount() {
        return this.provider != OAuthProvider.LOCAL;
    }
}