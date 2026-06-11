import React from 'react';
import styled from 'styled-components';

const HoursGridContainer = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr; /* PC 화면에서 2열 구조 */
  gap: 12px;
  column-gap: 24px;
  margin-top: 12px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const HourRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  background: #fafafa;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
`;

const TimeInput = styled.input`
  padding: 6px 4px;
  width: 60px;
  text-align: center;
  font-size: 13px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: ${(props) => (props.disabled ? '#f5f5f5' : 'white')};
  &:focus {
    outline: 1px solid #2d5a43;
  }
`;

const HolidayToggleBtn = styled.button`
  padding: 4px 10px;
  border-radius: 12px;
  border: 1px solid ${(props) => (props.$isHoliday ? '#ff4d4f' : '#2d5a43')};
  background: white;
  color: ${(props) => (props.$isHoliday ? '#ff4d4f' : '#2d5a43')};
  font-size: 12px;
  font-weight: 600;
  cursor: ${(props) => (props.disabled ? 'not-allowed' : 'pointer')};
  margin-left: auto;
  transition: all 0.1s;
`;

function StoreHoursForm({
  isEditing,
  operatingHours,
  onHoursChange,
  onToggleHoliday,
}) {
  return (
    <HoursGridContainer>
      {operatingHours?.map((item, idx) => (
        <HourRow key={item.day || idx}>
          <span style={{ width: '20px', fontWeight: '700', color: '#333' }}>
            {item.day}
          </span>
          <TimeInput
            type="text"
            placeholder="09:00"
            disabled={!isEditing || item.isHoliday}
            value={item.start || ''}
            onChange={(e) => onHoursChange(idx, 'start', e.target.value)}
          />
          <span style={{ color: '#aaa' }}>~</span>
          <TimeInput
            type="text"
            placeholder="19:00"
            disabled={!isEditing || item.isHoliday}
            value={item.end || ''}
            onChange={(e) => onHoursChange(idx, 'end', e.target.value)}
          />
          <HolidayToggleBtn
            type="button"
            disabled={!isEditing}
            $isHoliday={item.isHoliday}
            onClick={() => onToggleHoliday(idx)}
          >
            {item.isHoliday ? '휴무' : '영업'}
          </HolidayToggleBtn>
        </HourRow>
      ))}
    </HoursGridContainer>
  );
}

export default StoreHoursForm;
