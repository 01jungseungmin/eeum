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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoreStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 사장 회원가입 시 상점 기본 생성
     *
     * 이때 사업자 승인은 아직 완료되지 않았으므로
     * store.status는 TEMP_CLOSED 상태로 생성한다.
     */
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

    /**
     * 관리자 승인 시 상점 영업 가능 상태로 변경
     */
    public void open() {
        this.status = StoreStatus.OPEN;
    }

    /**
     * 임시 휴무 처리
     */
    public void tempClose() {
        this.status = StoreStatus.TEMP_CLOSED;
    }

    /**
     * 임시 휴무 해제 후 다시 영업
     */
    public void reopen() {
        this.status = StoreStatus.OPEN;
    }

    /**
     * 영업 종료
     */
    public void close() {
        this.status = StoreStatus.CLOSED;
    }

    /**
     * 상점 정보 설정
     */

    public void updateBusinessInfo(
            Category category,
            String description
    ) {
        this.category = category;
        this.description = description;
    }


    /**
     * 상점 기본 정보 수정
     */
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

    /**
     * 상점 위치 정보 수정
     */
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
}