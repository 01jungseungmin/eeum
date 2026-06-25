import React, { useState } from 'react';
import styled from 'styled-components';
import { Clock, Sliders } from 'lucide-react';
import SingleSlotEditModal from './DateSlotEditModal';

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
  padding: 14px 10px;
  border-bottom: 1px solid #f1f3f5;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.2s ease;

  &:last-child {
    border-bottom: none;
  }
  opacity: ${(props) => (props.$disabled ? 0.5 : 1)}; /* 차단된 슬롯 흐리게 */
`;

const TimeLabel = styled.span`
  width: 55px;
  font-size: 13px;
  font-weight: 700;
  color: #495057;
`;

const TableInfoContainer = styled.div`
  flex: 1;
  margin-left: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const BadgeGroup = styled.div`
  display: flex;
  gap: 6px;
`;

const TotalTableBadge = styled.span`
  font-size: 11px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 6px;
  background: ${(props) => (props.$disabled ? '#f1f3f5' : '#e4f2eb')};
  color: ${(props) => (props.$disabled ? '#868e96' : '#4CA771')};
`;

const CloseLabel = styled.span`
  font-size: 11px;
  color: #fa5252;
  font-weight: 700;
  background: #fff5f5;
  padding: 3px 8px;
  border-radius: 6px;
`;

const ActionBtn = styled.div`
  color: #adb5bd;
  display: flex;
  align-items: center;
  ${SlotRow}:hover & {
    color: #4ca771;
  } /* 행에 마우스 올리면 아이콘 색 변경 */
`;

export default function TimeSlotStatus({
  slotsData = [],
  selectedDate,
  dayOrders = [],
  defaultSettings,
  refreshDashboard,
}) {
  const validOrders = dayOrders.filter(
    (order) => order.status !== 'REJECTED' && order.status !== 'CANCELED',
  );

  const activeReservationTimes = validOrders.map((order) => {
    if (!order.visitTime) return '';
    return order.visitTime.substring(0, 5);
  });

  const visibleSlots = slotsData.filter((slot) => {
    const slotTime = slot.time?.substring(0, 5);
    const hasReservations = activeReservationTimes.includes(slotTime);
    const hasReservedCount = (slot.reservedTableCount || 0) > 0;
    const isManuallyModified = slot.enabled === false;

    return hasReservations || hasReservedCount || isManuallyModified;
  });

  return (
    <Container>
      <TitleZone>
        <h3>시간대별 테이블 현황</h3>
        <p>{selectedDate} · 클릭하여 개별 시간대를 조정·차단할 수 있습니다.</p>
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
          해당 날짜에 노출할 예약 시간대가 없습니다.
        </div>
      ) : (
        visibleSlots.map((slot) => {
          const slotTime = slot.time?.substring(0, 5);
          const totalReserved = slot.reservedTableCount || 0;
          const totalMax =
            slot.maxTableCount || defaultSettings?.defaultMaxTeamCount || 3;
          const isSlotEnabled = slot.enabled !== false;

          return (
            <SlotRow key={slot.time} $disabled={!isSlotEnabled}>
              <TimeLabel>{slotTime}</TimeLabel>
              <TableInfoContainer>
                {!isSlotEnabled ? (
                  <CloseLabel>예약 마감(차단됨)</CloseLabel>
                ) : (
                  <BadgeGroup>
                    <TotalTableBadge $disabled={!isSlotEnabled}>
                      테이블: {totalReserved} / {totalMax}개 사용중
                    </TotalTableBadge>
                  </BadgeGroup>
                )}

                <ActionBtn>
                  <Sliders size={13} />
                </ActionBtn>
              </TableInfoContainer>
            </SlotRow>
          );
        })
      )}
    </Container>
  );
}
