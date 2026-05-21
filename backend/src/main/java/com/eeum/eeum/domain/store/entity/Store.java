package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
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

    /**
     * 사장 계정
     * 사장 1명당 상점 1개 구조
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    /**
     * 상점 지역
     * 회원가입 시점에는 아직 선택하지 않을 수 있으므로 nullable 허용
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    /**
     * 위도
     * 주소 입력 후 지도 API로 변환하기 전까지 null 가능
     */
    @Column(name = "latitude")
    private Double latitude;

    /**
     * 경도
     * 주소 입력 후 지도 API로 변환하기 전까지 null 가능
     */
    @Column(name = "longitude")
    private Double longitude;

    /**
     * 상호명
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 사업장 소재지
     */
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    /**
     * 사업장 전화번호
     */
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    /**
     * 상점 소개
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 영업 시간
     */
    @Column(name = "business_hours", length = 255)
    private String businessHours;

    /**
     * 평균 평점
     */
    @Column(name = "rating", nullable = false)
    private Double rating;

    /**
     * 관심 수
     */
    @Column(name = "favorite_count", nullable = false)
    private Integer favoriteCount;

    /**
     * 리뷰 수
     */
    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    /**
     * 상점 운영 상태
     *
     * 승인 상태는 OwnerInfo.approvalStatus에서 관리하고,
     * Store.status는 승인 이후 상점 운영 상태를 관리한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoreStatus status;

    /**
     * 낙관적 락 버전
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 사장 회원가입 시 상점 기본 생성
     *
     * 이때 사업자 승인은 아직 완료되지 않았으므로
     * store.status는 INACTIVE 상태로 생성한다.
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
     * 영업 종료 / 폐업 처리
     */
    public void close() {
        this.status = StoreStatus.CLOSED;
    }

    /**
     * 상점 기본 정보 수정
     */
    public void updateBasicInfo(
            String name,
            String address,
            String phone,
            String description,
            String businessHours
    ) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.description = description;
        this.businessHours = businessHours;
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
}