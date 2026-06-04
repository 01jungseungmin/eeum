package com.eeum.eeum.domain.favorite.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 찜(Favorite) 엔티티.
 * Polymorphic 참조 패턴 — refType + refId 조합으로 다양한 도메인을 통합 관리한다.
 * refId는 FK 제약 없이 Service 레이어에서 존재 여부를 검증한다.
 *
 * UNIQUE(account_id, ref_type, ref_id) 제약으로 동일 대상 중복 찜을 방지한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "favorite",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_favorite_account_ref",
            columnNames = {"account_id", "ref_type", "ref_id"}
        )
    },
    indexes = {
        @Index(name = "idx_favorite_account_type", columnList = "account_id, ref_type"),
        @Index(name = "idx_favorite_ref", columnList = "ref_type, ref_id")
    }
)
public class Favorite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Long favoriteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 30)
    private FavoriteRefType refType;

    /**
     * FK 제약 없음 — Service에서 도메인 존재 여부 검증 후 저장
     */
    @Column(name = "ref_id", nullable = false)
    private Long refId;

    // ===================== 정적 팩토리 메서드 =====================

    public static Favorite create(Account account, FavoriteRefType refType, Long refId) {
        Favorite favorite = new Favorite();
        favorite.account = account;
        favorite.refType = refType;
        favorite.refId = refId;
        return favorite;
    }

    // ===================== 도메인 메서드 =====================

    /** 본인 찜 여부 확인 */
    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }

    /** 타입 + ID 일치 여부 (토글 검증용) */
    public boolean matches(FavoriteRefType refType, Long refId) {
        return this.refType == refType && this.refId.equals(refId);
    }
}