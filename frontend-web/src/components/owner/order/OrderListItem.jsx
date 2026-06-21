import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import {
  Package,
  Calendar,
  Clock,
  ChevronDown,
  ChevronUp,
  CreditCard,
} from 'lucide-react';
import { orderApi } from '../../../api/owner/orderApi';

const ItemWrapper = styled.div`
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
  margin-bottom: 12px;
`;

const HeaderRow = styled.div`
  padding: 18px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  transition: background 0.15s ease;
  &:hover {
    background: #f9fafb;
  }
`;

const LeftArea = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const IconBox = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background-color: ${(props) =>
    props.$isReservation ? '#eafaf1' : '#f0eeff'};
  color: ${(props) => (props.$isReservation ? '#10b981' : '#5c4fe5')};
  display: flex;
  align-items: center;
  justify-content: center;
`;

const IdRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const OrderId = styled.span`
  font-weight: 700;
  font-size: 15px;
  color: #111827;
  letter-spacing: -0.01em;
`;

const TypeBadge = styled.span`
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 6px;
  background: ${(props) => (props.$isReservation ? '#e6f4ea' : '#e8eaf6')};
  color: ${(props) => (props.$isReservation ? '#137333' : '#3f51b5')};
`;

const SummaryText = styled.p`
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
`;

const RightArea = styled.div`
  display: flex;
  align-items: center;
  gap: 20px;
`;

const PriceTime = styled.div`
  text-align: right;
  margin-right: 4px;
`;

const Price = styled.p`
  font-weight: 700;
  font-size: 16px;
  color: #111827;
`;

const Time = styled.p`
  font-size: 11px;
  color: #94a3b8;
  margin-top: 3px;
`;

const StatusBadge = styled.span`
  font-size: 12px;
  font-weight: 600;
  padding: 5px 14px;
  border-radius: 20px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: ${(props) => props.$cfg.bg};
  color: ${(props) => props.$cfg.color};
  border: 1px solid ${(props) => props.$cfg.border || 'transparent'};
`;

const DetailPanel = styled.div`
  border-top: 1px solid #f1f5f9;
  background: #fafafa;
  padding: 24px;
  display: grid;
  grid-template-columns: 1.1fr 1fr 0.9fr;
  gap: 24px;
`;

const Section = styled.div`
  display: flex;
  flex-direction: column;
`;

const SubTitle = styled.h4`
  font-size: 12px;
  color: #94a3b8;
  font-weight: 700;
  margin: 0 0 12px 0;
`;

const ProductList = styled.div`
  font-size: 13px;
  .row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    color: #334155;
  }
  .options {
    font-size: 11px;
    color: #94a3b8;
    margin-top: -6px;
    margin-bottom: 8px;
    padding-left: 4px;
  }
`;

const TotalRow = styled.div`
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  font-weight: 700;
  color: #111827;
  font-size: 14px;
`;

const CustomerInfo = styled.div`
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  margin-top: 12px;
  p {
    margin: 2px 0;
  }
`;

const Timeline = styled.div`
  border-left: 2px solid #e2e8f0;
  margin-left: 10px;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const TimelineItem = styled.div`
  position: relative;
  font-size: 13px;
  .state {
    font-weight: 700;
    color: #1e293b;
  }
  .time {
    font-size: 11px;
    color: #94a3b8;
    margin-left: 6px;
  }
  .desc {
    font-size: 11px;
    color: #64748b;
    margin-top: 2px;
  }
`;

const ClockIconWrapper = styled.div`
  position: absolute;
  left: -30px;
  top: 0;
  background: #fffdf0;
  border: 1px solid #fef3c7;
  border-radius: 50%;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const Dot = styled.div`
  position: absolute;
  left: -25px;
  top: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${(props) => props.$color || '#94a3b8'};
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px ${(props) => props.$color || '#e2e8f0'};
`;

const ActionSection = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 10px;
  padding-left: 16px;
`;

const Button = styled.button`
  width: 100%;
  padding: 12px;
  font-size: 13px;
  font-weight: 700;
  border-radius: 8px;
  cursor: pointer;
  border: none;
  transition: all 0.15s ease;

  ${(props) =>
    props.$variant === 'confirm' &&
    'background: #5bb38c; color: #fff; &:hover{background:#4aa27b;}'}
  ${(props) =>
    props.$variant === 'cancel' &&
    'background: #d94e73; color: #fff; &:hover{background:#c83d62;}'}
  ${(props) =>
    props.$variant === 'primary' &&
    'background: #4f7bf3; color: #fff; &:hover{background:#3b66de;}'}
  ${(props) =>
    props.$variant === 'secondary' &&
    'background: #fff; border: 1px solid #e2e8f0; color: #475569; &:hover{background:#f8fafc;}'}
`;

const CompletedText = styled.div`
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 500;
  padding: 32px 0;
  border: 1px dashed #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
`;

const PaymentBox = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  margin-top: 10px;
  font-size: 11px;
`;

const PayLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  color: #475569;
  .card-name {
    font-weight: 700;
    color: #1e293b;
  }
`;

const PayBadge = styled.span`
  background-color: #e6f4ea;
  color: #137333;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
`;

const RequestText = styled.p`
  margin-top: 4px;
  color: #e11d48; /* 요청사항 강조 색상 */
  font-weight: 600;
  span {
    color: #334155;
    font-weight: 400;
  }
`;

// 날짜 포맷 헬퍼 함수
const formatTime = (isoString) => {
  if (!isoString) return '';
  const date = new Date(isoString);
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

function OrderListItem({ order, isExpanded, onToggle, onStatusUpdate }) {
  const [detailData, setDetailData] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  const statusConfig = {
    PENDING: {
      text: '대기중',
      bg: '#fffbeb',
      color: '#d97706',
      border: '#fef3c7',
    },
    CONFIRMED: {
      text: '확인됨',
      bg: '#eff6ff',
      color: '#2563eb',
      border: '#dbeafe',
    },
    READY: {
      text: '준비완료',
      bg: '#f0fdf4',
      color: '#16a34a',
      border: '#dcfce7',
    },
    COMPLETED: {
      text: '완료',
      bg: '#f0fdf4',
      color: '#16a34a',
      border: '#dcfce7',
    },
    CANCELLED: {
      text: '취소됨',
      bg: '#fef2f2',
      color: '#dc2626',
      border: '#fee2e2',
    },
  };

  useEffect(() => {
    if (isExpanded) {
      const fetchDetail = async () => {
        try {
          setIsLoading(true);
          const response = await orderApi.getOwnerOrderDetail(order.orderId);
          if (response.data?.success) {
            setDetailData(response.data.data);
          }
        } catch (error) {
          console.error('주문 상세 정보를 불러오는 중 실패했습니다.', error);
        } finally {
          setIsLoading(false);
        }
      };

      fetchDetail();
    }
  }, [isExpanded, order.orderId]);

  const currentStatus = statusConfig[order.orderStatus] || {
    text: order.orderStatus,
    bg: '#f3f4f6',
    color: '#374151',
  };

  const firstItemName = order.items?.[0]?.productName || '상품 정보 없음';
  const itemsCount = order.items?.length || 0;

  // order_type 또는 데이터 내부 productType이 PREORDER인지 판별
  const isReservation =
    order.orderType === 'PREORDER' ||
    order.items?.[0]?.productType === 'PREORDER';

  return (
    <ItemWrapper>
      {/* 1. 상단 목록 요약 행 */}
      <HeaderRow onClick={onToggle}>
        <LeftArea>
          <IconBox $isReservation={isReservation}>
            {isReservation ? <Calendar size={18} /> : <Package size={18} />}
          </IconBox>
          <div>
            <IdRow>
              <OrderId>ORD-{order.orderId}</OrderId>
              <TypeBadge $isReservation={isReservation}>
                {isReservation ? '방문예약' : '구매주문'}
              </TypeBadge>
            </IdRow>
            <SummaryText>
              {order.customerNickname} · {firstItemName}
              {itemsCount > 1 ? ` 외 ${itemsCount - 1}건` : ''}
            </SummaryText>
          </div>
        </LeftArea>

        <RightArea>
          <PriceTime>
            <Price>{order.totalPrice.toLocaleString()}원</Price>
            <Time>{formatTime(order.createdAt)}</Time>
          </PriceTime>
          <StatusBadge $cfg={currentStatus}>
            {order.orderStatus === 'CONFIRMED' && (
              <span style={{ fontSize: '10px' }}>✓</span>
            )}
            {currentStatus.text}
          </StatusBadge>
          {isExpanded ? (
            <ChevronUp size={18} color="#94a3b8" />
          ) : (
            <ChevronDown size={18} color="#94a3b8" />
          )}
        </RightArea>
      </HeaderRow>

      {/* 2. 하단 상세 아코디언 패널 */}
      {isExpanded && (
        <DetailPanel>
          {isLoading ? (
            <div
              style={{
                gridColumn: 'span 3',
                textAlign: 'center',
                padding: '24px',
                color: '#94a3b8',
                fontSize: '13px',
              }}
            >
              상세 데이터를 불러오는 중입니다...
            </div>
          ) : detailData ? (
            <>
              {/* 컬럼 1: 주문 상품 및 결제/고객 정보 */}
              <Section>
                <SubTitle>주문 상품</SubTitle>
                <ProductList>
                  {detailData.items?.map((item) => (
                    <div key={item.orderItemId}>
                      <div className="row">
                        <span>
                          {item.productName} × {item.quantity}
                        </span>
                        <strong>
                          {item.lineTotalPrice.toLocaleString()}원
                        </strong>
                      </div>
                      {item.selectedOptionsText && (
                        <p className="options">↳ {item.selectedOptionsText}</p>
                      )}
                    </div>
                  ))}
                  <TotalRow>
                    <span>합계</span>
                    <span>{detailData.totalPrice.toLocaleString()}원</span>
                  </TotalRow>
                </ProductList>

                {/* 결제 정보 영역 */}
                <PaymentBox>
                  <PayLeft>
                    <CreditCard size={14} color="#64748b" />
                    <div>
                      <span className="card-name">
                        {isReservation
                          ? '현장 결제 예정'
                          : '신용/체크카드 결제'}
                      </span>
                    </div>
                  </PayLeft>
                  <PayBadge>
                    {isReservation ? '확인완료' : '결제 완료'}
                  </PayBadge>
                </PaymentBox>

                <CustomerInfo>
                  <p>
                    고객명:{' '}
                    {detailData.customerNickname || order.customerNickname}
                  </p>
                  <p>연락처: 010-****-1234</p>
                  {/* 실제 DB의 requestMessage 연동 */}
                  <RequestText>
                    요청사항: <span>{detailData.requestMessage || '없음'}</span>
                  </RequestText>
                </CustomerInfo>
              </Section>

              {/* 컬럼 2: 주문 처리 이력 (modifiedAt 기반 연동) */}
              <Section
                style={{
                  borderLeft: '1px solid #e2e8f0',
                  borderRight: '1px solid #e2e8f0',
                  padding: '0 24px',
                }}
              >
                <SubTitle>주문 처리 이력</SubTitle>
                <Timeline>
                  {/* 기본 접수 시간 */}
                  <TimelineItem>
                    <ClockIconWrapper>
                      <Clock size={11} color="#b45309" />
                    </ClockIconWrapper>
                    <div>
                      <p className="state">
                        대기중{' '}
                        <span className="time">
                          {formatTime(detailData.createdAt)}
                        </span>
                      </p>
                      <p className="desc">주문 접수 완료</p>
                    </div>
                  </TimelineItem>

                  {/* 사장님 확인 시점 (modifiedAt 매핑) */}
                  {['CONFIRMED', 'READY', 'COMPLETED'].includes(
                    detailData.orderStatus,
                  ) && (
                    <TimelineItem>
                      <Dot $color="#2563eb" />
                      <div>
                        <p className="state">
                          확인됨
                          <span className="time">
                            {formatTime(
                              detailData.modifiedAt || detailData.createdAt,
                            )}
                          </span>
                        </p>
                        <p className="desc">사장님 확인 완료</p>
                      </div>
                    </TimelineItem>
                  )}

                  {/* 준비 완료 처리 시점 */}
                  {['READY', 'COMPLETED'].includes(detailData.orderStatus) && (
                    <TimelineItem>
                      <Dot $color="#4f7bf3" />
                      <div>
                        <p className="state">
                          준비완료
                          <span className="time">
                            {formatTime(detailData.modifiedAt)}
                          </span>
                        </p>
                        <p className="desc">상품 준비 완료</p>
                      </div>
                    </TimelineItem>
                  )}

                  {/* 수령 완료 시점 */}
                  {detailData.orderStatus === 'COMPLETED' && (
                    <TimelineItem>
                      <Dot $color="#16a34a" />
                      <div>
                        <p className="state">
                          완료
                          <span className="time">
                            {formatTime(detailData.modifiedAt)}
                          </span>
                        </p>
                        <p className="desc">고객 수령 완료</p>
                      </div>
                    </TimelineItem>
                  )}

                  {/* 취소 처리 시점 */}
                  {detailData.orderStatus === 'CANCELLED' && (
                    <TimelineItem>
                      <Dot $color="#dc2626" />
                      <div>
                        <p className="state">
                          취소됨
                          <span className="time">
                            {formatTime(detailData.modifiedAt)}
                          </span>
                        </p>
                        <p className="desc">주문 취소 처리</p>
                      </div>
                    </TimelineItem>
                  )}
                </Timeline>
              </Section>

              {/* 컬럼 3: 상태별 액션 버튼 제어 */}
              <ActionSection>
                <SubTitle>주문 처리</SubTitle>
                {detailData.orderStatus === 'PENDING' && (
                  <>
                    <Button
                      $variant="confirm"
                      onClick={() =>
                        onStatusUpdate?.(detailData.orderId, 'CONFIRMED')
                      }
                    >
                      ✓ 주문 확인
                    </Button>
                    <Button
                      $variant="cancel"
                      onClick={() =>
                        onStatusUpdate?.(detailData.orderId, 'CANCELLED')
                      }
                    >
                      주문 취소
                    </Button>
                  </>
                )}
                {detailData.orderStatus === 'CONFIRMED' && (
                  <>
                    <Button
                      $variant="primary"
                      onClick={() =>
                        onStatusUpdate?.(detailData.orderId, 'READY')
                      }
                    >
                      준비 완료
                    </Button>
                    <Button
                      $variant="secondary"
                      onClick={() =>
                        onStatusUpdate?.(detailData.orderId, 'CANCELLED')
                      }
                    >
                      주문 취소
                    </Button>
                  </>
                )}
                {detailData.orderStatus === 'READY' && (
                  <>
                    <Button
                      $variant="primary"
                      onClick={() =>
                        onStatusUpdate?.(detailData.orderId, 'COMPLETED')
                      }
                    >
                      수령 완료
                    </Button>
                  </>
                )}
                {['COMPLETED', 'CANCELLED'].includes(
                  detailData.orderStatus,
                ) && <CompletedText>처리 완료된 주문입니다</CompletedText>}
              </ActionSection>
            </>
          ) : (
            <div
              style={{
                gridColumn: 'span 3',
                textAlign: 'center',
                padding: '24px',
                color: '#dc2626',
                fontSize: '13px',
              }}
            >
              데이터를 불러오지 못했습니다.
            </div>
          )}
        </DetailPanel>
      )}
    </ItemWrapper>
  );
}

export default OrderListItem;
