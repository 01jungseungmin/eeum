package com.eeum.eeum.domain.used.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 최종 확정 거래 장소.
 *
 * <p>채팅에서 오간 LOCATION 메시지는 <b>제안</b>이다. 마지막으로 보낸 장소가 곧 합의된
 * 장소는 아니므로(상대가 거절했을 수 있다), 양쪽이 합의한 하나를 여기 따로 남긴다.
 *
 * <p><b>키를 채팅방으로 잡지 않는다.</b> {@code ChatRoom}은 종료할 수 있고, 종료되면
 * 같은 (상품, 구매자) 조합으로 방이 새로 생긴다. 방에 매달면 한 번 종료·재생성되는
 * 순간 거래는 이어지는데 약속만 사라진다. 거래의 실제 식별자는 상품과 구매자 쌍이다.
 *
 * <p>약속 시각은 담지 않는다 — 지금 범위는 장소뿐이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "used_trade_appointment",
        uniqueConstraints = {
                // 재조율은 새 행이 아니라 기존 행 갱신이다. 거래당 확정 장소는 언제나 하나다.
                @UniqueConstraint(
                        name = "uk_used_trade_appointment_trade",
                        columnNames = {"used_product_id", "buyer_account_id"})
        }
)
public class UsedTradeAppointment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "used_trade_appointment_id")
    private Long usedTradeAppointmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_product_id", nullable = false)
    private UsedProduct usedProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_account_id", nullable = false)
    private Account buyer;

    // 표시용 장소명. 채팅과 마찬가지로 참여자에게만 보이므로 정확한 값을 담는다.
    @Column(name = "place_name", nullable = false, length = 255)
    private String placeName;

    // 지번/도로명 주소. 지도에서 직접 찍은 핀은 없을 수 있다.
    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    // 카카오 장소 ID. 검색을 거치지 않았으면 없다.
    @Column(name = "place_id", length = 50)
    private String placeId;

    // ===================== 정적 팩토리 메서드 =====================

    public static UsedTradeAppointment create(
            UsedProduct usedProduct,
            Account buyer,
            String placeName,
            Double latitude,
            Double longitude,
            String address,
            String placeId
    ) {
        validateBuyer(usedProduct, buyer);
        validatePlace(placeName, latitude, longitude);

        UsedTradeAppointment appointment = new UsedTradeAppointment();
        appointment.usedProduct = usedProduct;
        appointment.buyer = buyer;
        appointment.placeName = placeName;
        appointment.latitude = latitude;
        appointment.longitude = longitude;
        appointment.address = address;
        appointment.placeId = placeId;
        return appointment;
    }

    // ===================== 도메인 메서드 =====================

    /**
     * 약속 장소 재조율. 거래당 한 건이므로 새 행을 만들지 않고 이 행을 고친다.
     *
     * <p>이전 장소는 남기지 않는다 — 오간 제안은 채팅 메시지에 그대로 남아 있어
     * 여기서 이력을 중복해 들고 있을 이유가 없다.
     */
    public void relocate(
            String placeName,
            Double latitude,
            Double longitude,
            String address,
            String placeId
    ) {
        validatePlace(placeName, latitude, longitude);

        this.placeName = placeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.placeId = placeId;
    }

    // 거래 당사자 여부 — 판매자와 구매자만 약속 장소를 보고 바꿀 수 있다.
    public boolean isPartyOf(Long accountId) {
        return usedProduct.isOwnedBy(accountId) || buyer.getAccountId().equals(accountId);
    }

    // ===================== 내부 검증 =====================

    // 판매자가 자기 거래의 구매자로 들어오면 후기·약속이 한쪽으로 닫힌 거래가 만들어진다.
    private static void validateBuyer(UsedProduct usedProduct, Account buyer) {
        if (usedProduct == null || buyer == null) {
            throw new BusinessException(ErrorCode.USED_TRADE_APPOINTMENT_INVALID_PARTY);
        }
        if (usedProduct.isOwnedBy(buyer.getAccountId())) {
            throw new BusinessException(ErrorCode.USED_TRADE_APPOINTMENT_INVALID_PARTY);
        }
    }

    /**
     * 확정 장소는 게시글의 대략 위치와 달리 <b>비워둘 수 없다.</b> "여기서 만나기로 했다"를
     * 담는 값이라 이름과 좌표가 없으면 존재할 이유가 없다.
     *
     * <p>좌표 범위는 서버가 확인한다 — 프론트에서 검색 결과만 고르게 막아도 API를
     * 직접 호출하면 임의 좌표가 들어온다.
     */
    private static void validatePlace(String placeName, Double latitude, Double longitude) {
        if (placeName == null || placeName.isBlank() || latitude == null || longitude == null) {
            throw new BusinessException(ErrorCode.USED_TRADE_APPOINTMENT_INVALID_PLACE);
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.USED_TRADE_APPOINTMENT_INVALID_PLACE);
        }
    }
}
