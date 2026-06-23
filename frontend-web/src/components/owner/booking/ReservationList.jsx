import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { Clock } from 'lucide-react';
import { orderApi } from '../../../api/owner/orderApi';

const Container = styled.div`
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e9ecef;
`;

const FilterHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 {
    font-size: 14px;
    font-weight: 700;
  }
`;

const FilterButtons = styled.div`
  display: flex;
  gap: 6px;
`;

const FilterBtn = styled.button`
  padding: 6px 12px;
  font-size: 12px;
  border-radius: 6px;
  border: 1px solid ${(props) => (props.$active ? '#1a1a1a' : '#e9ecef')};
  background: ${(props) => (props.$active ? '#1a1a1a' : '#fff')};
  color: ${(props) => (props.$active ? '#fff' : '#495057')};
  cursor: pointer;
  font-weight: 500;
`;

const CardList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ReservationCard = styled.div`
  border: 1px solid ${(props) => (props.$isWaiting ? '#fab005' : '#e9ecef')};
  border-radius: 12px;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
`;

const InfoSection = styled.div`
  display: flex;
  gap: 16px;
  align-items: flex-start;
`;

const Avatar = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #4ca771;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
`;

const Details = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const NameRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  span.name {
    font-weight: 700;
    font-size: 14px;
    color: #1a1a1a;
  }
`;

const Badge = styled.span`
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  background: ${(props) =>
    props.$type === 'CONFIRMED'
      ? '#e6f4ea'
      : props.$type === 'PENDING'
        ? '#fff9db'
        : '#f1f3f5'};
  color: ${(props) =>
    props.$type === 'CONFIRMED'
      ? '#137333'
      : props.$type === 'PENDING'
        ? '#f59f00'
        : '#666'};
`;

const MenuText = styled.p`
  font-size: 13px;
  color: #495057;
  font-weight: 500;
`;

const MetaRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #868e96;
  span.time {
    display: flex;
    align-items: center;
    gap: 4px;
    font-weight: 500;
  }
`;

const ActionButtons = styled.div`
  display: flex;
  gap: 6px;
`;

const ActionBtn = styled.button`
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid ${(props) => (props.$isPrimary ? '#4CA771' : '#e9ecef')};
  background: ${(props) => (props.$isPrimary ? '#4CA771' : '#fff')};
  color: ${(props) => (props.$isPrimary ? '#fff' : '#495057')};
  &:hover {
    opacity: 0.9;
  }
`;

export default function ReservationList({ selectedDate }) {
  const [filter, setFilter] = useState('전체');
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);

  const getStatusParam = (currentFilter) => {
    if (currentFilter === '확정') return 'CONFIRMED';
    if (currentFilter === '대기') return 'PENDING';
    if (currentFilter === '취소') return 'CANCELED';
    return undefined;
  };

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const status = getStatusParam(filter);
      const response = await orderApi.getOwnerOrders({
        status,
        date: selectedDate,
      });

      if (response.data && response.data.success) {
        // [수정 포인트] 백엔드 데이터가 곧바로 배열로 오지 않거나 다른 Key에 감싸져 있을 수 있으므로 방어 로직 추가
        const resultData = response.data.data;

        if (Array.isArray(resultData)) {
          setOrders(resultData);
        } else if (resultData && Array.isArray(resultData.orders)) {
          // 만약 data: { orders: [...] } 형태로 들어올 경우 대응
          setOrders(resultData.orders);
        } else {
          // 배열이 전혀 아닐 경우 에러 방지를 위해 빈 배열 세팅
          setOrders([]);
        }
      }
    } catch (error) {
      console.error('주문 목록을 가져오는데 실패했습니다.', error);
      setOrders([]); // 에러 발생 시에도 빈 배열로 초기화하여 렌더링 터짐 방지
    } finally {
      setLoading(false);
    }
  }, [filter, selectedDate]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  const handleConfirm = async (orderId) => {
    if (!window.confirm('이 예약을 확정하시겠습니까?')) return;
    try {
      const response = await orderApi.confirmOrder(orderId);
      if (response.data.success) {
        alert('예약이 확정되었습니다.');
        fetchOrders();
      }
    } catch (error) {
      alert('주문 확정 실패');
    }
  };

  return (
    <Container>
      <FilterHeader>
        <h3>{selectedDate} 예약 내역</h3>
        <FilterButtons>
          {['전체', '확정', '대기', '취소'].map((type) => (
            <FilterBtn
              key={type}
              $active={filter === type}
              onClick={() => setFilter(type)}
            >
              {type}
            </FilterBtn>
          ))}
        </FilterButtons>
      </FilterHeader>

      <CardList>
        {loading ? (
          <div>로딩 중...</div>
        ) : !Array.isArray(orders) || orders.length === 0 ? ( // [수정 포인트] map 실행 전에 안전 검사 추가
          <div
            style={{
              textAlign: 'center',
              padding: '40px 0',
              color: '#999',
              fontSize: '13px',
            }}
          >
            해당 날짜의 예약 내역이 없습니다.
          </div>
        ) : (
          orders.map((order) => (
            <ReservationCard
              key={order.orderId}
              $isWaiting={order.status === 'PENDING'}
            >
              <InfoSection>
                <Avatar>
                  {order.customerName ? order.customerName[0] : '회'}
                </Avatar>
                <Details>
                  <NameRow>
                    <span className="name">
                      {order.customerName || '주문자'}
                    </span>
                    <Badge $type={order.status}>
                      {order.status === 'PENDING'
                        ? '대기'
                        : order.status === 'CONFIRMED'
                          ? '확정'
                          : '취소'}
                    </Badge>
                  </NameRow>
                  <MenuText>
                    {order.orderTitle || order.summaryText || '주문 상품 내역'}
                  </MenuText>
                  <MetaRow>
                    <span className="time">
                      <Clock size={12} /> {order.pickupTime || '시간 미지정'}
                    </span>
                    <span>{order.customerPhone}</span>
                  </MetaRow>
                </Details>
              </InfoSection>

              {order.status === 'PENDING' && (
                <ActionButtons>
                  <ActionBtn
                    $isPrimary
                    onClick={() => handleConfirm(order.orderId)}
                  >
                    확정
                  </ActionBtn>
                  <ActionBtn onClick={() => alert('거절 처리')}>거절</ActionBtn>
                </ActionButtons>
              )}
            </ReservationCard>
          ))
        )}
      </CardList>
    </Container>
  );
}
