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
 * 판매자 비활성화(정지·탈퇴) 시 중고 게시글 뒷정리.
 *
 * 판매자가 비활성이면 isPubliclyVisible()이 거짓이 되어 글이 전 화면에서 사라지는데,
 * 예약은 RESERVED로 남아 구매자가 볼 수도 없는 글을 기다리게 된다. 정지도 해제 시점이
 * 정해져 있지 않고 그동안 구매자가 할 수 있는 일이 없어 탈퇴와 같게 취소한다.
 * 게시글 자체는 지우지 않는다 — 공개 조회가 이미 걸러내고, 30일 안에 탈퇴를 취소하면
 * 글이 그대로 돌아와야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsedProductWithdrawalService {

    private final UsedProductRepository usedProductRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 호출 전제: 제재·탈퇴 트랜잭션이 열려 있고 대상 Account 행을 잠근 상태다.
     * 게시글 행은 ID 오름차순으로 잠근다 — 신고 조치가 반대 순서로 잡지 않도록.
     * 목록을 ID로만 읽는 이유: 엔티티로 읽으면 1차 캐시에 올라가 뒤이은 FOR UPDATE 조회가
     * 기존 인스턴스를 돌려줘 "잠근 뒤 재확인"이 성립하지 않는다.
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
