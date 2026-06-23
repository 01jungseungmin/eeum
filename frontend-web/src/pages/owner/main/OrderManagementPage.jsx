import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { AlertTriangle } from 'lucide-react';
import OrderSummaryCard from '../../../components/owner/order/OrderSummaryCard';
import FilterBar from '../../../components/owner/order/OrderFilterBar';
import OrderList from '../../../components/owner/order/OrderList';

import { orderApi } from '../../../api/owner/orderApi';

const Container = styled.div`
  padding: 24px;
  background-color: #f8f9fa;
  min-height: 100vh;
  font-family: sans-serif;
`;

const SummaryGrid = styled.div`
  display: flex;
  flex-direction: row;
  gap: 14px;
  width: 100%;
  margin-bottom: 20px;
`;

const AlertBanner = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: #fff9db;
  border: 1px solid #ffe3e3;
  padding: 14px 16px;
  border-radius: 8px;
  font-size: 13px;
  color: #b06d0f;
  margin-bottom: 20px;
`;

function OrderManagementPage() {
  const [orders, setOrders] = useState([]);
  const [filterType, setFilterType] = useState('전체 유형');
  const [searchTerm, setSearchTerm] = useState('');

  // 현황판 실시간 카운트 관리 상태값
  const [counts, setCounts] = useState({
    total: 0,
    waiting: 0,
    confirmed: 0,
    completed: 0,
    canceled: 0,
  });

  // API 데이터 패치 함수
  const fetchOrders = async () => {
    try {
      const response = await orderApi.getOwnerOrders({
        page: 0,
        size: 50,
      });

      if (response.data?.success) {
        const contentList = response.data.data.content || [];
        console.log(contentList);
        setOrders(contentList);

        setCounts({
          total: contentList.length,
          waiting: contentList.filter((o) => o.orderStatus === 'PENDING')
            .length,
          confirmed: contentList.filter(
            (o) => o.orderStatus === 'CONFIRMED' || o.orderStatus === 'READY',
          ).length,
          completed: contentList.filter((o) => o.orderStatus === 'COMPLETED')
            .length,
          canceled: contentList.filter((o) => o.orderStatus === 'CANCELLED')
            .length,
        });
      }
    } catch (error) {
      console.error('주문 목록을 불러오는 중 오류가 발생했습니다.', error);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, [filterType]);

  // 클라이언트 사이드 검색어 필터링
  const filteredOrders = orders.filter((order) => {
    const orderProductType = order.items?.[0]?.productType;
    const matchesType =
      filterType === '전체 유형' ||
      (filterType === '구매 주문' && orderProductType === 'SALE') ||
      (filterType === '방문 예약' && orderProductType === 'PREORDER'); // 스펙에 맞춰 PREORDER 또는 RESERVATION 대입

    const matchesSearch =
      String(order.orderId).toLowerCase().includes(searchTerm.toLowerCase()) ||
      order.customerNickname.toLowerCase().includes(searchTerm.toLowerCase());

    return matchesType && matchesSearch;
  });

  return (
    <Container>
      {/* 상단 현황판 */}
      <SummaryGrid>
        <OrderSummaryCard title="전체" count={counts.total} $isActive={true} />
        <OrderSummaryCard
          title="대기중"
          count={counts.waiting}
          badge="처리 필요"
        />
        <OrderSummaryCard title="확인됨" count={counts.confirmed} />
        <OrderSummaryCard title="완료" count={counts.completed} />
        <OrderSummaryCard title="취소됨" count={counts.canceled} />
      </SummaryGrid>

      {/* 알림 배너 */}
      {counts.waiting > 0 && (
        <AlertBanner>
          <AlertTriangle size={16} />
          <span>
            <strong>
              {counts.waiting}건의 주문이 확인을 기다리고 있습니다.
            </strong>{' '}
            빠른 처리를 부탁드립니다.
          </span>
        </AlertBanner>
      )}

      {/* 필터 및 검색 바 */}
      <FilterBar
        filterType={filterType}
        setFilterType={setFilterType}
        searchTerm={searchTerm}
        setSearchTerm={setSearchTerm}
      />

      {/* 💡 주문 리스트 컴포넌트 바인딩 및 핸들러 위임 */}
      <OrderList orders={filteredOrders} onRefresh={fetchOrders} />
    </Container>
  );
}

export default OrderManagementPage;
