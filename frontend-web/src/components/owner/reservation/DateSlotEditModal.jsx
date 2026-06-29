import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { X, Calendar, Power, AlertCircle } from 'lucide-react';
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
  z-index: 1200;
`;

const ModalContainer = styled.div`
  background: #fff;
  width: 440px;
  max-height: 80vh;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  font-family: 'Noto Sans KR', sans-serif;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  h3 {
    font-size: 16px;
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
  color: #868e96;
  margin-bottom: 16px;
`;

const ListArea = styled.div`
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
  margin-bottom: 20px;
  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-thumb {
    background: #e9ecef;
    border-radius: 3px;
  }
`;

const SlotItemRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 8px;
  border-bottom: 1px solid #f1f3f5;
  background: ${(props) => (props.$disabled ? '#f8f9fa' : '#fff')};
`;

const TimeBlock = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  span {
    font-size: 14px;
    font-weight: 700;
    color: ${(props) => (props.$disabled ? '#adb5bd' : '#495057')};
  }
`;

const ToggleButton = styled.button`
  background: ${(props) => (props.$active ? '#e4f2eb' : '#fff5f5')};
  color: ${(props) => (props.$active ? '#4CA771' : '#fa5252')};
  border: 1px solid ${(props) => (props.$active ? '#4CA771' : '#ffa8a8')};
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
`;

const InputWrapper = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  input {
    width: 55px;
    padding: 6px;
    border: 1px solid #ced4da;
    border-radius: 4px;
    text-align: center;
    font-size: 13px;
    font-weight: 600;
    &:disabled {
      background: #e9ecef;
      color: #adb5bd;
    }
  }
  span {
    font-size: 12px;
    color: #495057;
  }
`;

const Footer = styled.div`
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 10px;
`;
const ActionBtn = styled.button`
  padding: 12px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  background: ${(props) => (props.$save ? '#4CA771' : '#fff')};
  color: ${(props) => (props.$save ? '#fff' : '#495057')};
  border: 1px solid ${(props) => (props.$save ? '#4CA771' : '#ced4da')};
`;

export default function DateSlotEditModal({
  selectedDate,
  onClose,
  refreshDashboard,
}) {
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);

  // 날짜별 타임슬롯 API 연동 부분 구조 개선
  useEffect(() => {
    const fetchDateSlots = async () => {
      try {
        setLoading(true);
        const response = await reservationApi.getDateTimeSlots(selectedDate);

        if (response.data && response.data.success) {
          const rawData = response.data.data;
          setSlots(Array.isArray(rawData) ? rawData : []);
        } else if (Array.isArray(response.data)) {
          setSlots(response.data);
        }
      } catch (error) {
        console.error('날짜별 슬롯 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchDateSlots();
  }, [selectedDate]);

  // 특정 타임 수용 테이블 수 실시간 타이핑 핸들러
  const handleCountChange = (index, value) => {
    const updated = [...slots];
    updated[index].maxTeamCount = Math.max(0, parseInt(value) || 0);
    setSlots(updated);
  };

  // 특정 타임 예약 차단/오픈 스위치 토글 핸들러
  const handleToggleEnable = (index) => {
    const updated = [...slots];
    updated[index].enabled = !updated[index].enabled;
    setSlots(updated);
  };

  const handleSave = async () => {
    try {
      const payload = {
        date: selectedDate,
        slots: slots.map((s) => ({
          time: s.time,
          maxVisitorCount: Number(s.maxVisitorCount ?? s.maxTeamCount * 4),
          maxTeamCount: Number(s.maxTeamCount ?? 0),
          enabled: s.enabled !== false,
        })),
      };

      const response = await reservationApi.saveDateTimeSlots(payload);

      if (response.data && response.data.success) {
        alert(
          `${selectedDate}의 시간대별 예약 설정이 성공적으로 반영되었습니다.`,
        );
        if (refreshDashboard) refreshDashboard();
        onClose();
      } else {
        alert('서버 오류로 인해 설정이 저장되지 않았습니다.');
      }
    } catch (error) {
      console.error('개별 일자 예약 설정 저장 실패:', error);
      const errMsg =
        error.response?.data?.message || '설정 저장 중 오류가 발생했습니다.';
      alert(errMsg);
    }
  };

  return (
    <Overlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <Header>
          <h3>하루 시간대별 예약 조정</h3>
          <button onClick={onClose}>
            <X size={18} />
          </button>
        </Header>
        <Subtitle>
          <Calendar
            size={12}
            style={{
              display: 'inline',
              marginRight: '4px',
              verticalAlign: 'middle',
            }}
          />
          {selectedDate} 전용 특별 운영 상태 및 수용 테이블 수정
        </Subtitle>

        <ListArea>
          {loading ? (
            <div
              style={{
                textAlign: 'center',
                padding: '40px 0',
                fontSize: '13px',
                color: '#999',
              }}
            >
              시간대 데이터를 불러오는 중...
            </div>
          ) : slots.length === 0 ? (
            <div
              style={{
                textAlign: 'center',
                padding: '40px 0',
                fontSize: '13px',
                color: '#999',
              }}
            >
              <AlertCircle
                size={18}
                style={{ marginBottom: '6px', color: '#ccc' }}
              />
              <br />
              설정 가능한 시간대 목록이 비어있습니다.
            </div>
          ) : (
            slots.map((slot, index) => {
              const isEnabled = slot.enabled !== false;
              return (
                <SlotItemRow key={slot.time || index} $disabled={!isEnabled}>
                  <TimeBlock $disabled={!isEnabled}>
                    <span>{slot.time?.substring(0, 5)}</span>
                    <ToggleButton
                      $active={isEnabled}
                      onClick={() => handleToggleEnable(index)}
                    >
                      <Power size={11} />
                      {isEnabled ? '오픈됨' : '차단됨'}
                    </ToggleButton>
                  </TimeBlock>

                  <InputWrapper>
                    <span>최대 테이블 수:</span>
                    <input
                      type="number"
                      min="0"
                      disabled={!isEnabled}
                      value={slot.maxTeamCount ?? 0}
                      onChange={(e) => handleCountChange(index, e.target.value)}
                    />
                    <span>개</span>
                  </InputWrapper>
                </SlotItemRow>
              );
            })
          )}
        </ListArea>

        <Footer>
          <ActionBtn onClick={onClose}>취소</ActionBtn>
          <ActionBtn $save onClick={handleSave}>
            설정 저장
          </ActionBtn>
        </Footer>
      </ModalContainer>
    </Overlay>
  );
}
