package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedTradeAppointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsedTradeAppointmentRepository extends JpaRepository<UsedTradeAppointment, Long> {

    // 거래(상품 × 구매자)당 한 건이다. 채팅방이 아니라 이 쌍으로 찾는다 —
    // 방은 종료·재생성될 수 있어 약속의 키가 되지 못한다.
    Optional<UsedTradeAppointment> findByUsedProduct_UsedProductIdAndBuyer_AccountId(
            Long usedProductId, Long buyerAccountId);
}
