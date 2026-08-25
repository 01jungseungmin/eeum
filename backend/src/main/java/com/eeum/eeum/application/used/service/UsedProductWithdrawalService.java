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
     * 호출 전제: 탈퇴 트랜잭션이 이미 열려 있고 대상 Account 행을 잠근 상태다.
     * 게시글 행은 ID 오름차순으로 잠근다 — 신고 조치가 같은 행을 반대 순서로 잡지 않도록.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelReservationsForWithdrawal(Long sellerId) {
        List<UsedProduct> reserved = usedProductRepository
                .findBySeller_AccountIdAndStatusAndDeletedAtIsNullOrderByUsedProductIdAsc(
                        sellerId, UsedProductStatus.RESERVED);

        for (UsedProduct product : reserved) {
            // 잠금 후 다시 읽는다 — 목록을 읽은 뒤 신고 조치나 상태 변경이 끼어들 수 있다.
            UsedProduct locked = usedProductRepository
                    .findByUsedProductIdForUpdate(product.getUsedProductId())
                    .orElse(null);
            if (locked == null || locked.getStatus() != UsedProductStatus.RESERVED) {
                continue;
            }

            Account buyer = locked.getBuyer();
            locked.cancelReservation();

            // 상대가 없는 "예약중" 표시는 통보할 사람이 없다.
            if (buyer != null) {
                eventPublisher.publishEvent(new UsedProductReservationCancelledEvent(
                        locked.getUsedProductId(), buyer.getAccountId(), locked.getTitle()));
            }
        }

        if (!reserved.isEmpty()) {
            log.info("판매자 탈퇴로 예약 취소: sellerId={}, count={}", sellerId, reserved.size());
        }
    }
}
