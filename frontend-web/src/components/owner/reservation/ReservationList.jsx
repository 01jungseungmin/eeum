import React from 'react';
import styled from 'styled-components';
import { Clock } from 'lucide-react';
import { reservationApi } from '../../../api/owner/reservationApi';

const Container = styled.div`
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #e9ecef;
`;

const FilterHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h3 {
    font-size: 16px;
    font-weight: 700;
    color: #1a1a1a;
  }
`;

const FilterButtons = styled.div`
  display: flex;
  gap: 6px;
`;

const FilterBtn = styled.button`
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 20px;
  cursor: pointer;
  border: 1px solid ${(props) => (props.$active ? '#1a1a1a' : '#e9ecef')};
  background: ${(props) => (props.$active ? '#1a1a1a' : '#fff')};
  color: ${(props) => (props.$active ? '#fff' : '#868e96')};
  transition: all 0.2s;

  &:hover {
    background: ${(props) => (props.$active ? '#1a1a1a' : '#f8f9fa')};
  }
`;

const CardList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ReservationCard = styled.div`
  background: #fff;
  border: 1px solid ${(props) => (props.$isWaiting ? '#fab005' : '#e9ecef')};
  border-radius: 12px;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
`;

const InfoSection = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 12px;
`;

const Avatar = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #e4f2eb;
  color: #4ca771;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
`;

const Details = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const NameRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;

  .name {
    font-size: 14px;
    font-weight: 700;
    color: #1a1a1a;
  }
`;

const Badge = styled.span`
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;

  background: ${(props) => {
    if (props.$type === 'PENDING') return '#fff9db';
    if (props.$type === 'APPROVED' || props.$type === 'CONFIRMED')
      return '#e6f4ea';
    return '#f8f9fa';
  }};
  color: ${(props) => {
    if (props.$type === 'PENDING') return '#fab005';
    if (props.$type === 'APPROVED' || props.$type === 'CONFIRMED')
      return '#4CA771';
    return '#868e96';
  }};
`;

const MenuText = styled.p`
  font-size: 13px;
  color: #495057;
  font-weight: 500;
`;

const RequestMsg = styled.p`
  font-size: 12px;
  color: #fab005;
  background: #fff9db;
  padding: 6px 10px;
  border-radius: 6px;
  margin-top: 4px;
  font-weight: 500;
`;

const MetaRow = styled.div`
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #868e96;
  margin-top: 4px;
  align-items: center;

  .time {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #495057;
    font-weight: 600;
  }
`;

const ActionButtons = styled.div`
  display: flex;
  gap: 8px;
`;

const ActionBtn = styled.button`
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid ${(props) => (props.$isPrimary ? '#4CA771' : '#e9ecef')};
  background: ${(props) => (props.$isPrimary ? '#4CA771' : '#fff')};
  color: ${(props) => (props.$isPrimary ? '#fff' : '#495057')};
  transition: background 0.2s;

  &:hover {
    background: ${(props) => (props.$isPrimary ? '#3b935d' : '#f8f9fa')};
  }
`;

export default function ReservationList({
  selectedDate,
  orders = [],
  loading,
  filter,
  setFilter,
  refreshOrders,
  refreshTimeSlots,
}) {
  // 🟢 [확정/승인] 처리 함수
  const handleApprove = async (reservationId) => {
    if (!window.confirm('이 예약을 확정하시겠습니까?')) return;
    try {
      const response = await reservationApi.approveReservation(reservationId);
      if (response.data && response.data.success) {
        alert('예약이 성공적으로 확정되었습니다.');
        await refreshOrders();
        if (refreshTimeSlots) await refreshTimeSlots(selectedDate);
      }
    } catch (error) {
      alert('예약 확정 처리에 실패했습니다.');
    }
  };

  // 🔴 [거절] 처리 함수
  const handleReject = async (reservationId) => {
    const reason = window.prompt(
      '거절 사유를 입력해주세요:',
      '해당 시간에는 예약이 어렵습니다.',
    );
    if (reason === null) return;
    if (!reason.trim()) {
      alert('거절 사유는 필수 항목입니다.');
      return;
    }

    try {
      const response = await reservationApi.rejectReservation(
        reservationId,
        reason,
      );
      if (response.data && response.data.success) {
        alert('예약이 거절 처리되었습니다.');
        await refreshOrders();
        if (refreshTimeSlots) await refreshTimeSlots(selectedDate);
      }
    } catch (error) {
      alert('예약 거절 처리에 실패했습니다.');
    }
  };

  // 🎯 [핵심] 프론트엔드 단에서 대시보드 상태 버튼 종류에 따라 실시간 매핑 필터링
  const displayOrders = orders.filter((order) => {
    if (filter === '전체') return true;
    if (filter === '확정')
      return order.status === 'APPROVED' || order.status === 'CONFIRMED';
    if (filter === '대기') return order.status === 'PENDING';
    if (filter === '취소')
      return order.status === 'CANCELED' || order.status === 'REJECTED';
    return true;
  });

  return (
    <Container>
      <FilterHeader>
        <h3>예약 목록 ({displayOrders.length}건)</h3>
        <FilterButtons>
          {['전체', '확정', '대기', '취소'].map((type) => (
            <FilterBtn
              key={type}
              $active={filter === type}
              onClick={() => setFilter(type)} // 상위 state 변경 -> 실시간 리액트 리렌더링 트리거
            >
              {type}
            </FilterBtn>
          ))}
        </FilterButtons>
      </FilterHeader>

      <CardList>
        {loading ? (
          <div
            style={{
              textAlign: 'center',
              padding: '25px',
              color: '#666',
              fontSize: '13px',
            }}
          >
            데이터 로드 중...
          </div>
        ) : displayOrders.length === 0 ? (
          <div
            style={{
              textAlign: 'center',
              padding: '50px 0',
              color: '#999',
              fontSize: '13px',
            }}
          >
            해당 조건에 부합하는 예약 내역이 존재하지 않습니다.
          </div>
        ) : (
          displayOrders.map((order, idx) => {
            const uniqueKey = order.visitReservationId || `visit-${idx}`;

            return (
              <ReservationCard
                key={uniqueKey}
                $isWaiting={order.status === 'PENDING'}
              >
                <InfoSection>
                  <Avatar>
                    {order.customerName ? order.customerName[0] : '회'}
                  </Avatar>
                  <Details>
                    <NameRow>
                      <span className="name">
                        {order.customerName || '고객명 미지정'}
                      </span>
                      <Badge $type={order.status}>
                        {order.status === 'PENDING'
                          ? '대기'
                          : order.status === 'APPROVED' ||
                              order.status === 'CONFIRMED'
                            ? '확정'
                            : '취소'}
                      </Badge>
                    </NameRow>

                    <MenuText>방문 예약 · {order.visitorCount || 0}명</MenuText>

                    {order.requestMessage && (
                      <div>
                        <RequestMsg>
                          💡 요청사항: {order.requestMessage}
                        </RequestMsg>
                      </div>
                    )}

                    {(order.status === 'REJECTED' ||
                      order.status === 'CANCELED') &&
                      order.rejectReason && (
                        <div
                          style={{
                            fontSize: '12px',
                            color: '#e03131',
                            marginTop: '4px',
                            fontWeight: 500,
                          }}
                        >
                          ❌ 사유: {order.rejectReason}
                        </div>
                      )}

                    <MetaRow>
                      <span className="time">
                        <Clock size={12} />{' '}
                        {order.visitTime?.substring(0, 5) || '시간 미정'}
                      </span>
                      <span>{order.customerPhone || '연락처 없음'}</span>
                    </MetaRow>
                  </Details>
                </InfoSection>

                {/* 대기 상태인 카드 우측에만 [확정] / [거절] 제어 인터페이스 활성화 */}
                {order.status === 'PENDING' && (
                  <ActionButtons>
                    <ActionBtn
                      $isPrimary
                      onClick={() => handleApprove(order.visitReservationId)}
                    >
                      확정
                    </ActionBtn>
                    <ActionBtn
                      onClick={() => handleReject(order.visitReservationId)}
                    >
                      거절
                    </ActionBtn>
                  </ActionButtons>
                )}
              </ReservationCard>
            );
          })
        )}
      </CardList>
    </Container>
  );
}
