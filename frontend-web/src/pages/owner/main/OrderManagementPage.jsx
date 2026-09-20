import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { AlertTriangle } from 'lucide-react';
import OrderSummaryCard from '../../../components/owner/order/OrderSummaryCard';
import FilterBar from '../../../components/owner/order/OrderFilterBar';
import OrderList from '../../../components/owner/order/OrderList';

import { orderApi } from '../../../api/owner/orderApi';

// 카드 하나가 여러 주문 상태를 묶는다 (카운트 계산과 필터링이 같은 기준을 쓰도록 한곳에 둔다)
const STATUS_GROUPS = {
  WAITING: ['PENDING', 'PAID'],
  CONFIRMED: ['CONFIRMED', 'READY'],
  COMPLETED: ['COMPLETED'],
  CANCELED: ['CANCELLED'],
};

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
  // 상단 카드로 고르는 주문 상태: ALL | WAITING | CONFIRMED | COMPLETED | CANCELED
  const [statusFilter, setStatusFilter] = useState('ALL');
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
      // 서버가 정렬 없이 내려주므로(오래된 주문부터) 최신 주문 50건을 받도록 정렬을 요청한다
      const response = await orderApi.getOwnerOrders({
        page: 0,
        size: 50,
        sort: 'createdAt,desc',
      });

      if (response.data?.success) {
        // 같은 시각에 생성된 주문은 orderId 역순으로 맞춰 항상 최신 주문이 위에 오게 한다
        const contentList = [...(response.data.data.content || [])].sort(
          (a, b) =>
            new Date(b.createdAt) - new Date(a.createdAt) ||
            b.orderId - a.orderId,
        );
        setOrders(contentList);

        const countOf = (group) =>
          contentList.filter((o) => STATUS_GROUPS[group].includes(o.orderStatus))
            .length;

        setCounts({
          total: contentList.length,
          waiting: countOf('WAITING'),
          confirmed: countOf('CONFIRMED'),
          completed: countOf('COMPLETED'),
          canceled: countOf('CANCELED'),
        });
      }
    } catch (error) {
      console.error('주문 목록을 불러오는 중 오류가 발생했습니다.', error);
    }
  };

  useEffect(() => {
    queueMicrotask(() => fetchOrders());
  }, [filterType]);

  // 클라이언트 사이드 검색어 필터링
  const filteredOrders = orders.filter((order) => {
    const orderProductType = order.items?.[0]?.productType;
    const matchesType =
      filterType === '전체 유형' ||
      (filterType === '구매 주문' && orderProductType === 'SALE') ||
      (filterType === '방문 예약' && orderProductType === 'PREORDER'); // 스펙에 맞춰 PREORDER 또는 RESERVATION 대입

    const matchesStatus =
      statusFilter === 'ALL' ||
      STATUS_GROUPS[statusFilter].includes(order.orderStatus);

    const matchesSearch =
      String(order.orderId).toLowerCase().includes(searchTerm.toLowerCase()) ||
      order.customerNickname.toLowerCase().includes(searchTerm.toLowerCase());

    return matchesType && matchesStatus && matchesSearch;
  });

  return (
    <Container>
      {/* 상단 현황판 */}
      <SummaryGrid>
        <OrderSummaryCard
          title="전체"
          count={counts.total}
          $isActive={statusFilter === 'ALL'}
          onClick={() => setStatusFilter('ALL')}
        />
        <OrderSummaryCard
          title="대기중"
          count={counts.waiting}
          badge={counts.waiting > 0 ? '처리 필요' : undefined}
          $isActive={statusFilter === 'WAITING'}
          onClick={() => setStatusFilter('WAITING')}
        />
        <OrderSummaryCard
          title="확인됨"
          count={counts.confirmed}
          $isActive={statusFilter === 'CONFIRMED'}
          onClick={() => setStatusFilter('CONFIRMED')}
        />
        <OrderSummaryCard
          title="완료"
          count={counts.completed}
          $isActive={statusFilter === 'COMPLETED'}
          onClick={() => setStatusFilter('COMPLETED')}
        />
        <OrderSummaryCard
          title="취소됨"
          count={counts.canceled}
          $isActive={statusFilter === 'CANCELED'}
          onClick={() => setStatusFilter('CANCELED')}
        />
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
      <OrderList
        orders={filteredOrders}
        onRefresh={fetchOrders}
      />
    </Container>
  );
}

export default OrderManagementPage;
