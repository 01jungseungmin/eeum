import React, { useState } from 'react';
import styled from 'styled-components';
import OrderListItem from './OrderListItem';
import { orderApi } from '../../../api/owner/orderApi';

const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

function OrderList({ orders, onRefresh }) {
  const [expandedId, setExpandedId] = useState(null);

  const handleToggle = (orderId) => {
    setExpandedId(expandedId === orderId ? null : orderId);
  };

  const handleStatusUpdate = async (orderId, actionType) => {
    try {
      // 주문 확인 승인 API
      if (actionType === 'PENDING') {
        const response = await orderApi.confirmOrder(orderId);
        if (response.data?.success) {
          alert('주문을 승인하였습니다.');
          onRefresh?.();
        }
      }

      // 픽업 준비 완료 API
      else if (actionType === 'CONFIRMED') {
        const response = await orderApi.readyOrder(orderId);
        if (response.data?.success) {
          alert('구매 주문 상품의 준비 완료 처리가 완료되었습니다.');
          onRefresh?.();
        }
      }

      // 거래 완료 API
      else if (actionType === 'READY') {
        const response = await orderApi.completeOrder(orderId);
        if (response.data?.success) {
          alert('방문 예약 건에 대한 거래 완료 처리가 완료되었습니다.');
          onRefresh?.();
        }
      }

      // 주문 거절(취소) API
      else if (actionType === 'REJECT') {
        const rejectReason = prompt('주문 거절 사유를 입력해주세요.');
        if (rejectReason === null) return; // 취소 클릭 시 중단
        if (!rejectReason.trim()) {
          alert('거절 사유를 입력하셔야 거절 처리가 가능합니다.');
          return;
        }
        const response = await orderApi.rejectOrder(orderId, rejectReason);
        if (response.data?.success) {
          alert('주문이 거절(취소) 처리되었습니다.');
          onRefresh?.();
        }
      }
    } catch (error) {
      console.error('API 통신 오류:', error);
      alert('요청 처리 중 오류가 발생했습니다. 다시 시도해 주세요.');
    }
  };

  return (
    <ListContainer>
      {orders.map((order) => (
        <OrderListItem
          key={order.orderId}
          order={order}
          isExpanded={expandedId === order.orderId}
          onToggle={() => handleToggle(order.orderId)}
          onStatusUpdate={handleStatusUpdate}
        />
      ))}
    </ListContainer>
  );
}

export default OrderList;
