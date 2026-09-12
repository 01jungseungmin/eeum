package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;

public interface PaymentCancellationOperationRepository
        extends JpaRepository<PaymentCancellationOperation, Long> {

    Optional<PaymentCancellationOperation> findByOrder_OrderId(Long orderId);

    // 단계 전이는 항상 현재 행을 다시 읽어서 한다 — 준비 단계와 반영 단계가
    // 서로 다른 트랜잭션이라, 그 사이에 다른 경로가 같은 작업을 진전시켰을 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from PaymentCancellationOperation o
            where o.order.orderId = :orderId
            """)
    Optional<PaymentCancellationOperation> findByOrderIdWithPessimisticLock(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentCancellationOperation o WHERE o.paymentCancellationOperationId = :operationId")
    Optional<PaymentCancellationOperation> findByIdWithPessimisticLock(@Param("operationId") Long operationId);

    @Query("SELECT o.order.orderId FROM PaymentCancellationOperation o WHERE o.paymentCancellationOperationId = :operationId")
    Optional<Long> findOrderIdByOperationId(@Param("operationId") Long operationId);

    /**
     * 아직 마무리되지 않은 취소 작업. 정산 지급을 막을 때와, 무엇이 막고 있는지
     * 관리자에게 보여줄 때 같은 조회를 쓴다 — 막는 조건과 보여주는 목록이 어긋나면
     * 관리자가 해소할 수 없는 차단이 생긴다.
     */
    @Query("""
            select o from PaymentCancellationOperation o
            join fetch o.order
            where o.order.orderId in :orderIds and o.status <> :completedStatus
            order by o.order.orderId asc
            """)
    List<PaymentCancellationOperation> findUncompletedByOrderIds(
            @Param("orderIds") Collection<Long> orderIds,
            @Param("completedStatus") PaymentCancellationStatus completedStatus);
}
