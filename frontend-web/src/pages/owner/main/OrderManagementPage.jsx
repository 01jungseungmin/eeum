import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { AlertTriangle } from 'lucide-react';
import OrderSummaryCard from '../../../components/owner/order/OrderSummaryCard';
import FilterBar from '../../../components/owner/order/OrderFilterBar';
import OrderListItem from '../../../components/owner/order/OrderListItem';

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

const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

function OrderManagementPage() {
  // 백엔드 응답 리스트 content 데이터를 관리할 상태값
  const [orders, setOrders] = useState([]);
  const [filterType, setFilterType] = useState('전체 유형');
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedId, setExpandedId] = useState(null);

  // 현황판 실시간 카운트 관리를 위한 상태값
  const [counts, setCounts] = useState({
    total: 0,
    waiting: 0,
    confirmed: 0,
    completed: 0,
    canceled: 0,
  });

  // 1. API 데이터 fetch 함수 생성
  const fetchOrders = async () => {
    try {
      // 드롭다운 필터 조건에 맞춰 백엔드가 요구하는 파라미터 매핑 (필요 시 수정)
      let statusParam = undefined;
      if (filterType === '구매 주문') statusParam = 'SALE';
      if (filterType === '방문 예약') statusParam = 'RESERVATION';

      const response = await orderApi.getOwnerOrders({
        page: 0,
        size: 50, // 페이지 당 노출 사이즈 기본 지정
        // status: statusParam (백엔드 필터 파라미터 명세에 맞춰 활성화 가능)
      });

      if (response.data?.success) {
        const contentList = response.data.data.content || [];
        setOrders(contentList);

        // 백엔드 orderStatus 스펙 기반으로 실시간 카운트 계산 처리
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

  // 2. 컴포넌트 마운트 및 필터 타입 변경 시 데이터 백엔드 동기화
  useEffect(() => {
    fetchOrders();
  }, [filterType]);

  // 3. 상태 변경 요청 핸들러 (OrderListItem의 버튼 컴포넌트와 통신 연동용)
  const handleStatusUpdate = async (orderId, nextStatus) => {
    try {
      await orderApi.updateOrderStatus(orderId, nextStatus);
      alert('주문 상태 처리가 완료되었습니다.');
      fetchOrders(); // 성공 후 목록 새로고침
    } catch (error) {
      console.error('상태 변경 실패:', error);
      alert('상태 변경 처리 중 에러가 발생했습니다.');
    }
  };

  // 4. 클라이언트 사이드 검색어 매핑 필터링 (기존 틀 유지 - 백엔드 필드명 구조에 매칭)
  const filteredOrders = orders.filter((order) => {
    // 1순위: 드롭다운 유형 필터 (items 내부 첫 번째 아이템의 productType 기준 검증)
    const orderProductType = order.items?.[0]?.productType;
    const matchesType =
      filterType === '전체 유형' ||
      (filterType === '구매 주문' && orderProductType === 'SALE') ||
      (filterType === '방문 예약' && orderProductType === 'RESERVATION');

    // 2순위: 검색창 필터 (주문번호 및 고객 닉네임 기준)
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

      {/* 주문 리스트 */}
      <ListContainer>
        {filteredOrders.map((order) => (
          <OrderListItem
            key={order.orderId} // 백엔드 식별자인 orderId 매핑
            order={order}
            isExpanded={expandedId === order.orderId}
            onToggle={() =>
              setExpandedId(expandedId === order.orderId ? null : order.orderId)
            }
            onStatusUpdate={handleStatusUpdate} // 상태 제어 콜백 연동 전달
          />
        ))}
      </ListContainer>
    </Container>
  );
}

export default OrderManagementPage;
