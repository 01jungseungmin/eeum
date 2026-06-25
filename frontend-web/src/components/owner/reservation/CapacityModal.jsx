// CapacityModal.jsx
import React, { useState } from 'react';
import styled from 'styled-components';
import { X, Settings, Layers, CalendarDays } from 'lucide-react';
import { reservationApi } from '../../../api/owner/reservationApi';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1100;
`;

const ModalContainer = styled.div`
  background: #fff;
  width: 480px;
  max-height: 90vh;
  border-radius: 16px;
  padding: 28px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  font-family: 'Noto Sans KR', sans-serif;
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  h2 {
    font-size: 18px;
    font-weight: 700;
    color: #1a1a1a;
  }
  button {
    background: none;
    border: none;
    cursor: pointer;
    color: #adb5bd;
  }
`;

const Subtitle = styled.p`
  font-size: 12px;
  color: #999;
  margin-bottom: 24px;
`;

const ScrollArea = styled.div`
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
  margin-bottom: 24px;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #e9ecef;
    border-radius: 3px;
  }
`;

const FormGroup = styled.div`
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  border: 1px solid #f1f3f5;
`;

const GroupTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: #495057;
  margin-bottom: 14px;
`;

const InputRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  &:not(:last-child) {
    border-bottom: 1px dashed #e9ecef;
  }
  label {
    font-size: 13px;
    color: #495057;
    font-weight: 500;
  }
`;

const FieldControl = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  span {
    font-size: 13px;
    color: #495057;
    font-weight: 600;
  }
`;

const StyledInput = styled.input`
  width: 70px;
  padding: 8px;
  border: 1px solid #ced4da;
  border-radius: 6px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  &:focus {
    outline: none;
    border-color: #4ca771;
  }
`;

const StyledSelect = styled.select`
  width: 110px;
  padding: 8px 10px;
  border: 1px solid #ced4da;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a1a;
  background-color: #fff;
  &:focus {
    outline: none;
    border-color: #4ca771;
  }
`;

const ToggleSwitch = styled.input`
  width: 18px;
  height: 18px;
  accent-color: #4ca771;
  cursor: pointer;
`;

const TotalSummary = styled.div`
  margin-top: 10px;
  padding: 12px;
  background: #e4f2eb;
  border-radius: 8px;
  color: #4ca771;
  font-size: 13px;
  font-weight: 700;
  text-align: right;
`;

const FooterGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
`;

const FooterBtn = styled.button`
  padding: 14px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid ${(props) => (props.$primary ? '#4CA771' : '#e9ecef')};
  background: ${(props) => (props.$primary ? '#4CA771' : '#fff')};
  color: ${(props) => (props.$primary ? '#fff' : '#495057')};
`;

export default function CapacityModal({
  initialSettings,
  onClose,
  refreshSettings,
}) {
  // 상태 제어 변수들
  const [isEnabled, setIsEnabled] = useState(initialSettings?.enabled ?? true);
  const [sameDayReservationAllowed, setSameDayReservationAllowed] = useState(
    initialSettings?.sameDayReservationAllowed ?? true,
  );
  const [slotIntervalMinutes, setSlotIntervalMinutes] = useState(
    initialSettings?.slotIntervalMinutes || 30,
  );

  // 인원수 변수 제거하고 2, 4, 6인용 테이블 개수 상태 추가
  const [table2Seater, setTable2Seater] = useState(
    initialSettings?.table2Seater || 0,
  );
  const [table4Seater, setTable4Seater] = useState(
    initialSettings?.table4Seater || 0,
  );
  const [table6Seater, setTable6Seater] = useState(
    initialSettings?.table6Seater || 0,
  );

  // 백엔드로 넘겨줄 총 테이블(팀) 개수 합산
  const totalTablesCount =
    Number(table2Seater) + Number(table4Seater) + Number(table6Seater);

  const handleSaveSettings = async () => {
    try {
      const payload = {
        enabled: isEnabled,
        sameDayReservationAllowed: sameDayReservationAllowed,
        slotIntervalMinutes: Number(slotIntervalMinutes),
        cancelDeadlineMinutes: initialSettings?.cancelDeadlineMinutes || 0,
        startTime: initialSettings?.startTime || '10:00',
        endTime: initialSettings?.endTime || '20:00',

        // 테이블 개수 세부 데이터 전송
        table2Seater: Number(table2Seater),
        table4Seater: Number(table4Seater),
        table6Seater: Number(table6Seater),
        defaultMaxTeamCount: totalTablesCount, // 총 팀(테이블) 수 계산 데이터 매핑
      };

      const response = await reservationApi.updateVisitSettings(payload);
      if (response.data && response.data.success) {
        alert('방문 예약 정책이 성공적으로 저장되었습니다.');
        if (refreshSettings) {
          await refreshSettings();
        }
        onClose();
      }
    } catch (error) {
      console.error('설정 저장 실패:', error);
      alert('설정값 저장 도중 오류가 발생했습니다.');
    }
  };

  return (
    <Overlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <h2>방문 예약 정책 설정</h2>
          <button onClick={onClose}>
            <X size={20} />
          </button>
        </ModalHeader>
        <Subtitle>
          가게의 기본 예약 정책 및 타임별 수용 테이블 개수를 설정합니다.
        </Subtitle>

        <ScrollArea>
          {/* 기본 운영 상태 설정 */}
          <FormGroup>
            <GroupTitle>
              <CalendarDays size={15} color="#4CA771" />
              <span>기본 운영 상태 설정</span>
            </GroupTitle>
            <InputRow>
              <label>방문 예약 기능 사용</label>
              <ToggleSwitch
                type="checkbox"
                checked={isEnabled}
                onChange={(e) => setIsEnabled(e.target.checked)}
              />
            </InputRow>
            <InputRow>
              <label>당일 예약 신청 허용</label>
              <ToggleSwitch
                type="checkbox"
                checked={sameDayReservationAllowed}
                onChange={(e) => setSameDayReservationAllowed(e.target.checked)}
              />
            </InputRow>
          </FormGroup>

          {/* [기존 유지] 2. 예약 타임 간격 */}
          <FormGroup>
            <GroupTitle>
              <Settings size={15} color="#4CA771" />
              <span>예약 타임 간격</span>
            </GroupTitle>
            <InputRow>
              <label>방문 단위 간격</label>
              <StyledSelect
                value={slotIntervalMinutes}
                onChange={(e) => setSlotIntervalMinutes(Number(e.target.value))}
              >
                <option value={30}>30분 단위</option>
                <option value={60}>1시간 단위</option>
                <option value={90}>1시간 30분 단위</option>
              </StyledSelect>
            </InputRow>
          </FormGroup>

          <FormGroup>
            <GroupTitle>
              <Layers size={15} color="#4CA771" />
              <span>슬롯당 최대 수용량 (테이블 수)</span>
            </GroupTitle>

            <InputRow>
              <label>2인용 테이블 제한 수</label>
              <FieldControl>
                <StyledInput
                  type="number"
                  min="0"
                  value={table2Seater}
                  onChange={(e) =>
                    setTable2Seater(Math.max(0, parseInt(e.target.value) || 0))
                  }
                />
                <span>개</span>
              </FieldControl>
            </InputRow>

            <InputRow>
              <label>4인용 테이블 제한 수</label>
              <FieldControl>
                <StyledInput
                  type="number"
                  min="0"
                  value={table4Seater}
                  onChange={(e) =>
                    setTable4Seater(Math.max(0, parseInt(e.target.value) || 0))
                  }
                />
                <span>개</span>
              </FieldControl>
            </InputRow>

            <InputRow>
              <label>6인용 테이블 제한 수</label>
              <FieldControl>
                <StyledInput
                  type="number"
                  min="0"
                  value={table6Seater}
                  onChange={(e) =>
                    setTable6Seater(Math.max(0, parseInt(e.target.value) || 0))
                  }
                />
                <span>개</span>
              </FieldControl>
            </InputRow>

            <TotalSummary>
              타임당 총 운영 테이블: {totalTablesCount}개
            </TotalSummary>
          </FormGroup>
        </ScrollArea>

        <FooterGrid>
          <FooterBtn onClick={onClose}>취소</FooterBtn>
          <FooterBtn $primary onClick={handleSaveSettings}>
            저장
          </FooterBtn>
        </FooterGrid>
      </ModalContainer>
    </Overlay>
  );
}
