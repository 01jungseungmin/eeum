import React from 'react';
import styled from 'styled-components';

const Container = styled.div`
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e9ecef;
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
  padding: 12px 0;
  border-bottom: 1px solid #f1f3f5;
  opacity: ${(props) =>
    props.$isClosed ? 0.4 : 1}; /* DOM 누수 해결을 위해 $ 기호 적용 */
  &:last-child {
    border-bottom: none;
  }
`;

const TimeLabel = styled.span`
  width: 60px;
  font-size: 13px;
  font-weight: 700;
  color: #495057;
`;

const GaugeContainer = styled.div`
  flex: 1;
  margin-left: 12px;
  position: relative;
`;

const GaugeText = styled.span`
  font-size: 11px;
  color: #868e96;
  display: block;
  margin-bottom: 4px;
`;

const ProgressBar = styled.div`
  height: 6px;
  background: #e9ecef;
  border-radius: 3px;
  overflow: hidden;
  position: relative;
`;

const ProgressFill = styled.div`
  height: 100%;
  background: ${(props) => (props.$isFull ? '#e03131' : '#4CA771')};
  width: ${(props) => props.$percentage}%; /* 수치 스트링 변환 우회 완료 */
`;

const StatusBadge = styled.span`
  font-size: 11px;
  font-weight: 600;
  margin-left: 12px;
  color: ${(props) => (props.$disabled ? '#868e96' : '#fab005')};
`;

export default function TimeSlotStatus({ slotsData, selectedDate }) {
  return (
    <Container>
      <TitleZone>
        <h3>시간대별 현황</h3>
        <p>{selectedDate} · 슬롯 클릭으로 예약 목록 확인</p>
      </TitleZone>

      {slotsData && slotsData.length === 0 ? (
        <div
          style={{
            fontSize: '12px',
            color: '#999',
            textAlign: 'center',
            padding: '20px 0',
          }}
        >
          설정된 시간대가 없습니다.
        </div>
      ) : (
        slotsData?.map((slot) => {
          const maxCount = slot.maxVisitorCount || 1;
          const reservedCount = slot.reservedVisitorCount || 0;
          const percentage = Math.min((reservedCount / maxCount) * 100, 100);

          return (
            <SlotRow
              key={slot.timeSlotId}
              $isClosed={slot.closed || !slot.enabled}
            >
              <TimeLabel>{slot.time}</TimeLabel>
              <GaugeContainer>
                <GaugeText>
                  {reservedCount}/{maxCount}명
                </GaugeText>
                <ProgressBar>
                  <ProgressFill
                    $percentage={percentage}
                    $isFull={reservedCount >= maxCount}
                  />
                </ProgressBar>
              </GaugeContainer>
              {!slot.enabled && <StatusBadge $disabled>중지</StatusBadge>}
              {slot.closed && slot.enabled && <StatusBadge>마감</StatusBadge>}
            </SlotRow>
          );
        })
      )}
    </Container>
  );
}
