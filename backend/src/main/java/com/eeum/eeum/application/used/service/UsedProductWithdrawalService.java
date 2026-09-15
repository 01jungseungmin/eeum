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

/** 판매자 비활성화 때 예약을 취소하고 게시글은 보존한다. 복구 시 게시글을 되살려야 한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsedProductWithdrawalService {

    private final UsedProductRepository usedProductRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 잠금 순서를 고정하고, 잠근 뒤 상태를 다시 확인해 예약을 취소한다. */
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
