import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { orderApi } from '../../../api/owner/orderApi';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
  }
  .more {
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #52c41a;
    cursor: pointer;
    font-weight: 600;
  }
`;

const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const OrderItem = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;

  .left-side {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #5fa07e;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    font-size: 13px;
  }
  .info {
    .name {
      font-size: 13px;
      font-weight: 600;
      color: #262626;
    }
    .time {
      font-size: 11px;
      color: #bfbfbf;
      margin-top: 2px;
    }
  }
  .right-side {
    text-align: right;
  }
  .price {
    font-size: 14px;
    font-weight: 700;
    color: #262626;
    margin-bottom: 4px;
  }
`;

const StatusTag = styled.span`
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 10px;
  font-weight: 600;
  background-color: ${(props) =>
    props.$type === '확인됨'
      ? '#e6f7ff'
      : props.$type === '대기중'
        ? '#fff7e6'
        : props.$type === '취소됨' || props.$type === '주문만료'
          ? '#fff1f0'
          : '#f6ffed'};
  color: ${(props) =>
    props.$type === '확인됨'
      ? '#1890ff'
      : props.$type === '대기중'
        ? '#faad14'
        : props.$type === '취소됨' || props.$type === '주문만료'
          ? '#ff4d4f'
          : '#52c41a'};
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 30px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

// 백엔드 OrderStatus → 화면 표시용 상태 라벨
const STATUS_LABEL = {
  PENDING: '대기중',
  PAID: '대기중',
  CONFIRMED: '확인됨',
  READY: '확인됨',
  COMPLETED: '완료',
  CANCELLED: '취소됨',
  EXPIRED: '주문만료',
};

// 상대 시간 포맷 (NotificationItem.jsx의 formatTimeAgo와 동일한 규칙)
const formatTimeAgo = (dateString) => {
  if (!dateString) return '';
  const diffMinutes = Math.floor((new Date() - new Date(dateString)) / 60000);

  if (diffMinutes < 1) return '방금 전';
  if (diffMinutes < 60) return `${diffMinutes}분 전`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;

  return `${Math.floor(diffHours / 24)}일 전`;
};

function RecentOrders() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchRecentOrders = async () => {
    try {
      setLoading(true);
      const response = await orderApi.getOwnerOrders({
        page: 0,
        size: 3,
        sort: 'createdAt,desc',
      });

      if (response.data?.success) {
        setOrders(response.data.data.content || []);
      }
    } catch (error) {
      console.error('최근 주문 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    queueMicrotask(() => fetchRecentOrders());
  }, []);

  return (
    <Card>
      <Header>
        <h3>최근 주문</h3>
        <span
          className="more"
          onClick={() => navigate('/order-management')}
        >
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>
      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : orders.length === 0 ? (
        <EmptyText>최근 주문 내역이 없습니다.</EmptyText>
      ) : (
        <ListContainer>
          {orders.map((order) => {
            const itemName = order.items?.[0]?.productName || '주문 상품';
            const extraCount = (order.items?.length || 1) - 1;
            const statusLabel = STATUS_LABEL[order.orderStatus] || '완료';

            return (
              <OrderItem key={order.orderId}>
                <div className="left-side">
                  <div className="avatar">
                    {order.customerNickname?.[0] || '?'}
                  </div>
                  <div className="info">
                    <div className="name">
                      {order.customerNickname} · {itemName}
                      {extraCount > 0 ? ` 외 ${extraCount}건` : ''}
                    </div>
                    <div className="time">
                      {formatTimeAgo(order.createdAt)}
                    </div>
                  </div>
                </div>
                <div className="right-side">
                  <div className="price">
                    {(order.totalPrice ?? 0).toLocaleString()}원
                  </div>
                  <StatusTag $type={statusLabel}>{statusLabel}</StatusTag>
                </div>
              </OrderItem>
            );
          })}
        </ListContainer>
      )}
    </Card>
  );
}

export default RecentOrders;
