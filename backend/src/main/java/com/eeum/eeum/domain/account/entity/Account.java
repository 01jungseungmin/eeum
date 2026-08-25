package com.eeum.eeum.domain.account.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
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
                @UniqueConstraint(name = "uk_account_nickname", columnNames = "nickname"),
                // OAuth 계정 중복 생성 방지 — LOCAL 계정은 provider_id가 null이라 (MySQL은 NULL을 중복 허용) 영향 없음
                @UniqueConstraint(name = "uk_account_provider", columnNames = {"provider", "provider_id"})
        },
        indexes = { //인덱스 primary_region_id 컬럼으로 검색할 일이 있을 때 더 빠르게 찾기 위한 설정
                @Index(name = "idx_account_primary_region", columnList = "primary_region_id"),
                // 개인정보 파기 대상 조회 전용 — 조건(status, anonymized_at, deleted_at) 뒤에
                // keyset 커서(account_id)를 둬서 배치마다 이어서 읽는다.
                @Index(name = "idx_account_anonymize_target",
                        columnList = "status, anonymized_at, deleted_at, account_id")
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

    // 개인정보 파기 시각. WITHDRAWN만으로는 "유예 중"과 "파기 완료"를 구분할 수 없어
    // 스케줄러가 같은 계정을 반복 처리한다.
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

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

    public static Account createOAuthPendingUser(
            String email,
            String nickname,
            String profileImageUrl,
            OAuthProvider provider,
            String providerId
    ) {
        Account account = new Account();
        account.email = email;
        account.password = null;
        account.name = "";
        account.nickname = nickname;
        account.phone = "";
        account.provider = provider;
        account.providerId = providerId;
        account.profileImageUrl = profileImageUrl != null ? profileImageUrl : DEFAULT_PROFILE_IMAGE_URL;
        account.role = AccountRole.ROLE_USER;
        account.status = AccountStatus.PENDING;
        account.emailVerified = true;
        return account;
    }

    // 추가 정보 입력 완료 후 업데이트
    public void completeOAuthProfile(String name, String phone, String nickname) {
        this.name = name;
        this.phone = phone;
        if (nickname != null) this.nickname = nickname;
        this.status = AccountStatus.ACTIVE;
    }

    public static Account createOwner(
            String email,
            String encodedPassword,
            String name,
            String phone
    ) {
        Account account = new Account();
        account.email = email;
        account.password = encodedPassword;
        account.name = name;
        account.phone = phone;
        account.provider = OAuthProvider.LOCAL;
        account.providerId = null;
        account.profileImageUrl = DEFAULT_PROFILE_IMAGE_URL;

        // 사장 회원가입 직후에는 일반 회원 권한으로 시작
        // 사업자 정보 승인 후 approveOwner()를 통해 ROLE_OWNER로 변경
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

    /**
     * 탈퇴 취소 — 유예 기간 안에서만 되돌릴 수 있다.
     *
     * <p>개인정보가 이미 파기된 계정은 되돌리지 않는다. 되살리면 email·이름·전화가 지워진 채
     * ACTIVE가 되어, 로그인도 안 되고 다른 사용자에게는 "탈퇴한 회원"으로 보이는 계정이 남는다.
     */
    public void cancelWithdrawal() {
        if (isAnonymized()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_ANONYMIZED);
        }
        this.status = AccountStatus.ACTIVE;
        this.deletedAt = null;
    }

    /**
     * 개인정보 파기 — 행은 남기고 식별 가능한 값만 지운다.
     *
     * <p>계정 행을 물리 삭제하려면 이 계정을 참조하는 18개 테이블(주문·결제·신고·채팅 등)을
     * 함께 정리해야 하는데, 주문·결제는 정산과 보존 의무가 걸려 지울 수 없다.
     * 파기해야 하는 것은 식별 정보이지 활동 이력이 아니므로, 행을 남기고 값만 지운다.
     *
     * <p>email·nickname에는 UNIQUE 제약이 있다. 고정값으로 지우면 두 번째 탈퇴자부터 충돌하고
     * 같은 이메일로 재가입할 수도 없으므로, 계정 ID를 섞어 유일성을 만든다.
     */
    public void anonymize() {
        this.email = "deleted_" + this.accountId + "@removed.local";
        this.nickname = "탈퇴한회원_" + this.accountId;
        this.name = "탈퇴한 회원";
        this.phone = "";
        this.password = null;
        this.providerId = null;
        this.profileImageUrl = DEFAULT_PROFILE_IMAGE_URL;
        this.fcmToken = null;
        this.primaryRegionId = null;
        this.anonymizedAt = LocalDateTime.now();
    }

    public boolean isAnonymized() {
        return this.anonymizedAt != null;
    }

    public void withdraw() {
        this.status = AccountStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
        // 기기 토큰을 여기서 지운다. 익명화(30일 후)까지 미루면 그동안 탈퇴자 휴대폰으로
        // 푸시가 계속 나간다 — 상대가 아직 활성인 채팅방에 메시지를 보내는 것만으로도 발생한다.
        this.fcmToken = null;
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

    // 쓰기 경로 공통 가드 — 상태별로 구분해서 던진다.
    // !isActive()를 한 덩어리로 묶으면 가입 미완료(PENDING) 계정까지 "정지된 계정"으로 응답한다.
    public void assertWritable() {
        switch (this.status) {
            case ACTIVE -> { }
            case WITHDRAWN -> throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
            case SUSPENDED -> throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            case PENDING -> throw new BusinessException(ErrorCode.ACCOUNT_SIGNUP_INCOMPLETE);
        }
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public boolean isWithdrawn() {
        return this.status == AccountStatus.WITHDRAWN;
    }

    public boolean isSuspended() {
        return this.status == AccountStatus.SUSPENDED;
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
