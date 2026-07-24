import React from 'react';
import styled from 'styled-components';
import { Clock } from 'lucide-react';

const Container = styled.div`
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e9ecef;
  font-family: 'Noto Sans KR', sans-serif;
`;

const TitleZone = styled.div`
  margin-bottom: 16px;
  h3 {
    font-size: 14px;
    font-weight: 700;
    color: #1a1a1a;
    margin-bottom: 4px;
  }
  p {
    font-size: 11px;
    color: #999;
  }
`;

const SlotRow = styled.div`
  display: flex;
  align-items: center;
  padding: 12px 10px;
  border-bottom: 1px solid #f1f3f5;
  border-radius: 8px;

  &:last-child {
    border-bottom: none;
  }
  opacity: ${(props) => (props.$disabled ? 0.5 : 1)};
`;

const TimeLabel = styled.span`
  width: 50px;
  font-size: 13px;
  font-weight: 700;
  color: #495057;
`;

const TableInfoContainer = styled.div`
  flex: 1;
  margin-left: 8px;
  display: flex;
  align-items: center;
`;

const BadgeGroup = styled.div`
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
`;

const CapacityBadge = styled.span`
  font-size: 11px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 6px;

  background: ${(props) =>
    props.$disabled ? '#f1f3f5' : props.$isFull ? '#fff0f0' : '#e4f2eb'};
  color: ${(props) =>
    props.$disabled ? '#868e96' : props.$isFull ? '#e53935' : '#4ca771'};
  border: 1px solid
    ${(props) =>
      props.$disabled ? '#e9ecef' : props.$isFull ? '#ffcdd2' : '#c8e6c9'};
`;

const CloseLabel = styled.span`
  font-size: 11px;
  color: #fa5252;
  font-weight: 700;
  background: #fff5f5;
  padding: 3px 8px;
  border-radius: 6px;
`;

export default function TimeSlotStatus({
  slotsData = [],
  selectedDate,
  dayOrders = [],
  defaultSettings,
}) {
  // 대기(PENDING), 거절(REJECTED)을 제외하고 '승인된 예약'만 필터링
  const approvedOrders = dayOrders.filter(
    (order) => order.status === 'APPROVED',
  );

  // 승인된 예약이 존재하는 시간대만 추출
  const activeReservationTimes = approvedOrders.map((order) => {
    if (!order.visitTime) return '';
    return order.visitTime.substring(0, 5);
  });

  // 화면에 표시할 시간대 필터링
  const visibleSlots = slotsData.filter((slot) => {
    const slotTime = slot.time?.substring(0, 5);
    const hasApprovedReservations = activeReservationTimes.includes(slotTime);
    const isManuallyModified = slot.enabled === false;

    // 승인된 예약이 있거나 수동으로 차단된 시간대만 화면에 표시
    return hasApprovedReservations || isManuallyModified;
  });

  // 인원수를 파싱하는 안전한 함수
  const getHeadCount = (order) => {
    const count =
      order.headCount ??
      order.partySize ??
      order.guestCount ??
      order.peopleCount ??
      order.people ??
      1;

    if (typeof count === 'string') {
      const parsed = parseInt(count.replace(/[^0-9]/g, ''), 10);
      return isNaN(parsed) ? 1 : parsed;
    }
    return Number(count);
  };

  // 인원수별 인석 매칭
  const getCapacityByPeople = (count) => {
    if (count <= 2) return 2; // 1~2명 -> 2인석
    if (count <= 4) return 4; // 3~4명 -> 4인석
    return 6; // 5명 이상 -> 6인석
  };

  return (
    <Container>
      <TitleZone>
        <h3>시간대별 테이블 현황</h3>
        <p>{selectedDate} · 승인 확정된 예약의 테이블 사용 현황입니다.</p>
      </TitleZone>

      {visibleSlots.length === 0 ? (
        <div
          style={{
            fontSize: '12px',
            color: '#999',
            textAlign: 'center',
            padding: '30px 0',
          }}
        >
          <Clock size={20} style={{ color: '#ccc', marginBottom: '8px' }} />
          <br />
          해당 날짜에 승인된 예약 시간대가 없습니다.
        </div>
      ) : (
        visibleSlots.map((slot) => {
          const slotTime = slot.time?.substring(0, 5);
          const isSlotEnabled = slot.enabled !== false;

          // 해당 시간대의 '승인된' 예약만 추출
          const slotOrders = approvedOrders.filter(
            (o) => o.visitTime?.substring(0, 5) === slotTime,
          );

          // 승인된 예약에 대해서만 인석별 테이블 사용량 집계
          const usedMap = { 2: 0, 4: 0, 6: 0 };
          slotOrders.forEach((order) => {
            const count = getHeadCount(order);
            const cap = getCapacityByPeople(count);
            usedMap[cap] = (usedMap[cap] || 0) + 1;
          });

          // 설정된 총 테이블 수
          const totalMap = {
            2: defaultSettings?.table2Seater ?? 2,
            4: defaultSettings?.table4Seater ?? 3,
            6: defaultSettings?.table6Seater ?? 1,
          };

          return (
            <SlotRow key={slot.time} $disabled={!isSlotEnabled}>
              <TimeLabel>{slotTime}</TimeLabel>
              <TableInfoContainer>
                {!isSlotEnabled ? (
                  <CloseLabel>예약 마감(차단됨)</CloseLabel>
                ) : (
                  <BadgeGroup>
                    {[2, 4, 6].map((cap) => {
                      const used = usedMap[cap] || 0;
                      const total = totalMap[cap] || 0;
                      const isFull = used >= total && total > 0;

                      return (
                        <CapacityBadge
                          key={cap}
                          $isFull={isFull}
                          $disabled={!isSlotEnabled}
                        >
                          {cap}인석 {used}/{total}
                        </CapacityBadge>
                      );
                    })}
                  </BadgeGroup>
                )}
              </TableInfoContainer>
            </SlotRow>
          );
        })
      )}
    </Container>
  );
}
