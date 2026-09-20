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
  availableSlots = [],
  selectedDate,
}) {
  // 백엔드가 계산한 시간대별 잔여 테이블 현황 (HH:mm 기준으로 조회)
  const availabilityByTime = {};
  availableSlots.forEach((slot) => {
    availabilityByTime[slot.time?.substring(0, 5)] = slot;
  });

  // 예약된 테이블이 있거나 수동으로 차단된 시간대만 화면에 표시
  const visibleSlots = slotsData.filter((slot) => {
    const availability = availabilityByTime[slot.time?.substring(0, 5)];
    const hasReservedTables = (availability?.reservedTableCount ?? 0) > 0;
    const isManuallyModified = slot.enabled === false;
    return hasReservedTables || isManuallyModified;
  });

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
          <Clock
            size={20}
            style={{ color: '#ccc', marginBottom: '8px' }}
          />
          <br />
          해당 날짜에 승인된 예약 시간대가 없습니다.
        </div>
      ) : (
        visibleSlots.map((slot) => {
          const slotTime = slot.time?.substring(0, 5);
          const isSlotEnabled = slot.enabled !== false;
          const tableAvailabilities =
            availabilityByTime[slotTime]?.tableAvailabilities ?? [];

          return (
            <SlotRow
              key={slot.time}
              $disabled={!isSlotEnabled}
            >
              <TimeLabel>{slotTime}</TimeLabel>
              <TableInfoContainer>
                {!isSlotEnabled ? (
                  <CloseLabel>예약 마감(차단됨)</CloseLabel>
                ) : (
                  <BadgeGroup>
                    {tableAvailabilities.map((table) => {
                      const isFull =
                        table.totalCount > 0 && table.availableCount === 0;

                      return (
                        <CapacityBadge
                          key={table.capacity}
                          $isFull={isFull}
                          $disabled={!isSlotEnabled}
                        >
                          {table.capacity}인석 {table.reservedCount}/
                          {table.totalCount}
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
