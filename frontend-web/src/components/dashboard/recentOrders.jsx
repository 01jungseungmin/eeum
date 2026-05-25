import React from 'react';
import styled from 'styled-components';
import { ChevronRight } from 'lucide-react';

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
        : '#f6ffed'};
  color: ${(props) =>
    props.$type === '확인됨'
      ? '#1890ff'
      : props.$type === '대기중'
        ? '#faad14'
        : '#52c41a'};
`;

const orders = [
  {
    id: 1,
    user: '이*민',
    item: '김치찌개 반찬 세트',
    time: '14분 전',
    price: '18,000원',
    status: '확인됨',
  },
  {
    id: 2,
    user: '박*수',
    item: '불고기 반찬 (300g)',
    time: '28분 전',
    price: '12,000원',
    status: '대기중',
  },
  {
    id: 3,
    user: '최*진',
    item: '잡채 (200g)',
    time: '1시간 전',
    price: '24,000원',
    status: '완료',
  },
  {
    id: 4,
    user: '김*영',
    item: '계란말이 (1팩)',
    time: '1시간 전',
    price: '9,000원',
    status: '대기중',
  },
];

function RecentOrders() {
  return (
    <Card>
      <Header>
        <h3>최근 주문</h3>
        <span className="more">
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>
      <ListContainer>
        {orders.map((order) => (
          <OrderItem key={order.id}>
            <div className="left-side">
              <div className="avatar">{order.user[0]}</div>
              <div className="info">
                <div className="name">
                  {order.user} · {order.item}
                </div>
                <div className="time">{order.time}</div>
              </div>
            </div>
            <div className="right-side">
              <div className="price">{order.price}</div>
              <StatusTag $type={order.status}>{order.status}</StatusTag>
            </div>
          </OrderItem>
        ))}
      </ListContainer>
    </Card>
  );
}

export default RecentOrders;
