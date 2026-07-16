import React, { useState } from 'react';
import styled from 'styled-components';
import { Heart, Bell, Star, ChevronDown, ChevronUp } from 'lucide-react';
import { customerApi } from '../../../api/owner/customerApi'; // 경로를 프로젝트 구조에 맞춰 유지해주세요.

const Tr = styled.tr`
  border-bottom: ${(props) => (props.$isOpen ? 'none' : '1px solid #f1f5f9')};
  background-color: ${(props) => (props.$isOpen ? '#fafafa' : 'transparent')};
  cursor: pointer;
  &:hover {
    background-color: #f8fafc;
  }
`;

const Td = styled.td`
  padding: 14px 16px;
  font-size: 14px;
`;
const UserFlex = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;
const Avatar = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #e2e8f0;
  color: #475569;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
`;
const UserName = styled.div`
  font-weight: 600;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;
  .user-id {
    font-size: 10px;
    color: #94a3b8;
    font-weight: 400;
  }
`;
const UserPhone = styled.div`
  font-size: 12px;
  color: #94a3b8;
`;
const BadgeFlex = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const Badge = styled.span`
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background-color: ${(props) =>
    props.$type === 'REGULAR' || props.$type === 'NORMAL'
      ? '#fce8e6'
      : props.$type === 'NEW'
        ? '#e8f0fe'
        : '#f1f5f9'};
  color: ${(props) =>
    props.$type === 'REGULAR' || props.$type === 'NORMAL'
      ? '#d93025'
      : props.$type === 'NEW'
        ? '#1a73e8'
        : '#64748b'};
`;

const IconGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
`;
const RatingFlex = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #d97706;
  font-weight: 500;
  font-size: 12px;
`;

const DetailButton = styled.button`
  background: none;
  border: 1px solid ${(props) => (props.$isOpen ? '#10b981' : '#cbd5e0')};
  color: ${(props) => (props.$isOpen ? '#10b981' : '#64748b')};
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #ffffff;
  &:hover {
    border-color: #10b981;
    color: #10b981;
  }
`;

const HistoryRow = styled.tr`
  background-color: #fdfdfd;
  border-bottom: 1px solid #f1f5f9;
`;
const HistoryTd = styled.td`
  padding: 16px 24px 20px 24px;
`;
const HistoryTitle = styled.div`
  font-size: 13px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 10px;
`;
const HistoryList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const HistoryItem = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #ffffff;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.01);
`;

const HistoryDate = styled.span`
  font-size: 13px;
  color: #94a3b8;
  width: 140px;
`;
const HistoryMenu = styled.span`
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  flex: 1;
`;
const HistoryPrice = styled.span`
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  margin-right: 12px;
`;

const StatusBadge = styled.span`
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background-color: ${(props) =>
    props.$status === 'COMPLETED'
      ? '#e6f4ea'
      : props.$status === 'CANCELLED'
        ? '#fce8e6'
        : '#fff3cd'};
  color: ${(props) =>
    props.$status === 'COMPLETED'
      ? '#137333'
      : props.$status === 'CANCELLED'
        ? '#d93025'
        : '#856404'};
`;

export default function CustomerRow({ customer }) {
  const [isOpen, setIsOpen] = useState(false);
  const [orders, setOrders] = useState([]);
  const [loadingOrders, setLoadingOrders] = useState(false);

  // 1. 아코디언 토글 및 이력 API 조회 핸들러
  const handleToggle = async (e) => {
    e.stopPropagation();

    const nextState = !isOpen;
    setIsOpen(nextState);

    // 열릴 때만 비동기로 API에서 주문 이력 조회 수행
    if (nextState) {
      try {
        setLoadingOrders(true);
        const response = await customerApi.getCustomerOrders(
          customer.customerId,
        ); //

        if (response?.data?.success) {
          // 백엔드 pageable 데이터 구조(content 내부 배열) 매핑
          setOrders(response.data.data.content || []);
        }
      } catch (error) {
        console.error('고객별 주문 내역 조회 실패:', error);
      } finally {
        setLoadingOrders(false);
      }
    }
  };

  const getTypeText = (type) => {
    if (type === 'REGULAR' || type === 'NORMAL') return '단골';
    if (type === 'NEW') return '신규';
    return '일반';
  };

  // 2. 주문이 완료(COMPLETED)/취소(CANCELLED) 등 한글 상태 맵핑용 함수
  const getStatusText = (status) => {
    switch (status) {
      case 'COMPLETED':
        return '완료';
      case 'CANCELLED':
        return '취소';
      case 'PENDING':
        return '대기';
      default:
        return '진행중';
    }
  };

  // 3. API 응답의 여러 아이템 목록을 가공해서 보여주는 헬퍼 (예: '아메리카노 외 2건' 또는 '스프가 맛있는 우유 x2')
  const getOrderMenuName = (items = []) => {
    if (items.length === 0) return '주문 항목 없음';
    const firstItem = items[0];
    const firstItemText = `${firstItem.productName} x${firstItem.quantity}`;
    if (items.length === 1) return firstItemText;
    return `${firstItemText} 외 ${items.length - 1}건`;
  };

  // 4. 날짜 문자열 포맷 간소화 (2026-07-16T09:54:18 ➡️ 2026-07-16 09:54)
  const formatDate = (isoString) => {
    if (!isoString) return '-';
    return isoString.replace('T', ' ').substring(0, 16);
  };

  return (
    <>
      <Tr $isOpen={isOpen} onClick={handleToggle}>
        <Td style={{ paddingLeft: '24px' }}>
          <UserFlex>
            <Avatar>
              {customer.maskedName ? customer.maskedName[0] : '고'}
            </Avatar>
            <div>
              <UserName>
                {customer.maskedName}
                <span className="user-id">#{customer.customerId}</span>
              </UserName>
              <UserPhone>{customer.maskedPhone}</UserPhone>
            </div>
          </UserFlex>
        </Td>
        <Td>
          <BadgeFlex>
            <Badge $type={customer.customerType}>
              {getTypeText(customer.customerType)}
            </Badge>
            <IconGroup>
              {customer.favorite && (
                <Heart size={14} color="#e53e3e" fill="#e53e3e" />
              )}
              {customer.chatParticipant && (
                <Bell size={14} color="#4c51bf" fill="#4c51bf" />
              )}
            </IconGroup>
          </BadgeFlex>
        </Td>
        <Td
          style={{ textAlign: 'center', fontWeight: '500', color: '#4a5568' }}
        >
          {customer.totalOrderCount || 0}건
        </Td>
        <Td style={{ textAlign: 'right', fontWeight: '700', color: '#2d3748' }}>
          {(customer.totalOrderAmount || 0).toLocaleString()}원
        </Td>
        <Td style={{ textAlign: 'center' }}>
          {customer.averageRating ? (
            <RatingFlex>
              <Star size={12} color="#ecc94b" fill="#ecc94b" />
              {customer.averageRating.toFixed(1)} ({customer.reviewCount || 0})
            </RatingFlex>
          ) : (
            <span style={{ color: '#cbd5e0' }}>
              - ({customer.reviewCount || 0})
            </span>
          )}
        </Td>
        <Td style={{ textAlign: 'center', fontSize: '12px', color: '#718096' }}>
          {customer.lastOrderDate || '-'}
        </Td>
        <Td style={{ paddingRight: '24px', textAlign: 'center' }}>
          <DetailButton $isOpen={isOpen} onClick={handleToggle}>
            주문 이력{' '}
            {isOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </DetailButton>
        </Td>
      </Tr>

      {isOpen && (
        <HistoryRow>
          <HistoryTd colSpan={7}>
            <HistoryTitle>주문 이력 ({orders.length}건)</HistoryTitle>
            <HistoryList>
              {loadingOrders ? (
                <div
                  style={{
                    fontSize: '13px',
                    color: '#64748b',
                    padding: '8px',
                    textAlign: 'center',
                  }}
                >
                  주문 목록을 불러오는 중...
                </div>
              ) : orders.length > 0 ? (
                orders.map((order, idx) => (
                  <HistoryItem key={order.orderId || idx}>
                    <HistoryDate>{formatDate(order.createdAt)}</HistoryDate>
                    <HistoryMenu>{getOrderMenuName(order.items)}</HistoryMenu>
                    <HistoryPrice>
                      {(order.totalPrice || 0).toLocaleString()}원
                    </HistoryPrice>
                    <StatusBadge $status={order.orderStatus}>
                      {getStatusText(order.orderStatus)}
                    </StatusBadge>
                  </HistoryItem>
                ))
              ) : (
                <div
                  style={{ fontSize: '13px', color: '#94a3b8', padding: '8px' }}
                >
                  주문 기록이 존재하지 않습니다.
                </div>
              )}
            </HistoryList>
          </HistoryTd>
        </HistoryRow>
      )}
    </>
  );
}
