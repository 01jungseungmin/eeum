package com.eeum.eeum.application.used.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.event.UsedProductReservationCancelledEvent;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 판매자 탈퇴 시 중고 게시글 뒷정리.
 *
 * <p>사용자 삭제 경로({@code UsedProductService.delete})는 "예약 중이라는 건 상대가 거래를
 * 기다리고 있다는 뜻"이라며 RESERVED 글의 삭제를 막고 예약을 먼저 취소하게 한다.
 * 탈퇴도 판매자가 사라지는 것은 같은데 그동안 게시글을 건드리지 않아, 예약이 잡힌 채
 * 글만 전 화면에서 사라지고 구매자는 아무 통보도 받지 못했다.
 *
 * <p>정지와 탈퇴 <b>양쪽</b>에서 호출한다. 판매자가 비활성이면 {@code isPubliclyVisible()}이
 * 거짓이 되어 게시글이 전 화면에서 사라지는데, 예약은 RESERVED로 남아 구매자가 볼 수도
 * 없는 글을 기다리게 된다. 정지는 해제될 수 있지만 기간이 정해져 있지 않고, 그동안 구매자가
 * 할 수 있는 일이 없으므로 탈퇴와 같게 취소한다.
 *
 * <p>여기서 예약을 취소하고 상대에게 알린다. 게시글 자체는 지우지 않는다 —
 * 판매자 상태가 비활성이면 모든 공개 조회가 이미 걸러내고, 30일 안에 탈퇴를 취소하면
 * 글이 그대로 돌아와야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsedProductWithdrawalService {

    private final UsedProductRepository usedProductRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 호출 전제: 제재·탈퇴 트랜잭션이 이미 열려 있고 대상 Account 행을 잠근 상태다.
     * 게시글 행은 ID 오름차순으로 잠근다 — 신고 조치가 같은 행을 반대 순서로 잡지 않도록.
     *
     * <p><b>목록을 ID로만 읽는 이유가 중요하다.</b> 엔티티로 읽으면 영속성 컨텍스트에 올라가고,
     * 뒤이은 {@code findByUsedProductIdForUpdate}는 FOR UPDATE 락은 잡아도 1차 캐시의
     * 기존 인스턴스를 돌려준다 — 새로 읽은 행 상태가 버려져 "잠근 뒤 재확인"이 성립하지 않는다.
     * ID만 읽으면 잠금 조회가 곧 첫 조회라 상태를 실제로 다시 본다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelReservationsForSellerInactivation(Long sellerId) {
        List<Long> reservedIds = usedProductRepository
                .findReservedProductIdsBySeller(sellerId, UsedProductStatus.RESERVED);

        int cancelled = 0;
        for (Long usedProductId : reservedIds) {
            UsedProduct locked = usedProductRepository
                    .findByUsedProductIdForUpdate(usedProductId)
                    .orElse(null);

            // 잠그는 사이 신고 조치가 글을 지우거나 상태를 바꿨을 수 있다.
            // 삭제된 글까지 취소하면 "예약이 취소되었습니다" 알림이 사라진 글에 대해 나간다.
            if (locked == null
                    || locked.isDeleted()
                    || locked.getStatus() != UsedProductStatus.RESERVED) {
                continue;
            }

            Account buyer = locked.getBuyer();
            locked.cancelReservation();
            cancelled++;

            // 상대가 없는 "예약중" 표시는 통보할 사람이 없다.
            if (buyer != null) {
                eventPublisher.publishEvent(new UsedProductReservationCancelledEvent(
                        locked.getUsedProductId(), buyer.getAccountId(), locked.getTitle()));
            }
        }

        if (cancelled > 0) {
            log.info("판매자 비활성화로 예약 취소: sellerId={}, count={}", sellerId, cancelled);
        }
    }
}
