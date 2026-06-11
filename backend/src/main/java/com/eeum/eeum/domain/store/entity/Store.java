package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "store")
public class Store extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_id")
    private Long storeId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="category_id",nullable = true)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "rating", nullable = false)
    private Double rating;

    @Column(name = "favorite_count", nullable = false)
    private Integer favoriteCount;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    @Column(name = "visit_reservation_enabled", nullable = false)
    private boolean visitReservationEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoreStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 사장 회원가입 시 상점 기본 생성
    public static Store createForOwnerSignup(
            Account account,
            String name,
            String address,
            String phone
    ) {
        Store store = new Store();
        store.account = account;
        store.name = name;
        store.address = address;
        store.phone = phone;
        store.rating = 0.0;
        store.favoriteCount = 0;
        store.reviewCount = 0;
        store.status = StoreStatus.TEMP_CLOSED;
        return store;
    }

    // 상점 영업 가능 상태로 변경
    public void open() {
        this.status = StoreStatus.OPEN;
    }

    // 임시 상태로 변경
    public void tempClose() {
        this.status = StoreStatus.TEMP_CLOSED;
    }

    // 임시 휴무 해제 후 다시 영업 상태로 변경
    public void reopen() {
        this.status = StoreStatus.OPEN;
    }

    // 영업 종료 상태로 변경
    public void close() {
        this.status = StoreStatus.CLOSED;
    }

    // 상점 정보 설정
    public void updateBusinessInfo(
            Category category,
            String description
    ) {
        this.category = category;
        this.description = description;
    }


    // 상점 기본 정보 수정
    public void updateBasicInfo(
            String name,
            String address,
            String phone,
            String description
    ) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.description = description;
    }

    // 상점 위치 정보 수정
    public void updateLocation(
            Region region,
            Double latitude,
            Double longitude
    ) {
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void updateCategory(Category category) {
        this.category = category;
    }
    public void suspend() {
        this.status = StoreStatus.SUSPENDED;
    }

    public void activate() {
        this.status = StoreStatus.TEMP_CLOSED;
    }

    // ===================== 리뷰/평점 도메인 메서드 =====================

    // 평균 평점 재계산 리뷰 작성/수정/삭제 후 새로운 평균과 리뷰 수를 전달받아 갱신
    // @param newRating 새 평균 평점 (소수점 1자리 반올림)
    // @param newCount  새 리뷰 수
    public void updateRating(double newRating, int newCount) {
        this.rating = Math.round(newRating * 10.0) / 10.0;
        this.reviewCount = newCount;
    }

    //리뷰 작성 시 리뷰 수 1 증가
    public void increaseReviewCount() {
        this.reviewCount = this.reviewCount + 1;
    }

    //리뷰 삭제 시 리뷰 수 1 감소 (음수 방지)
    public void decreaseReviewCount() {
        if (this.reviewCount > 0) {
            this.reviewCount = this.reviewCount - 1;
        }
    }

    //상점 소유권 확인
    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }
}