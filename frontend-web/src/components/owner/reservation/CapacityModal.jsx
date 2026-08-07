import React, { useState, useEffect } from 'react';
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
    &:hover {
      color: #1a1a1a;
    }
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

const LoadingText = styled.div`
  text-align: center;
  padding: 16px 0;
  font-size: 13px;
  color: #868e96;
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
  &:disabled {
    background: #adb5bd;
    border-color: #adb5bd;
    cursor: not-allowed;
  }
`;

export default function CapacityModal({
  initialSettings,
  onClose,
  refreshSettings,
}) {
  const [isEnabled, setIsEnabled] = useState(initialSettings?.enabled ?? true);
  const [sameDayReservationAllowed, setSameDayReservationAllowed] = useState(
    initialSettings?.sameDayReservationAllowed ?? true,
  );
  const [slotIntervalMinutes, setSlotIntervalMinutes] = useState(
    initialSettings?.slotIntervalMinutes || 30,
  );

  // 인원별 테이블 개수 상태 ({ 2: 4, 4: 6, ... })
  const [capacityMap, setCapacityMap] = useState({ 2: 0, 4: 0, 6: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  // 백엔드 요약 API 호출 (/tables/summary)
  useEffect(() => {
    const fetchTableSummary = async () => {
      try {
        setIsLoading(true);
        const response = await reservationApi.getTableSummary();

        if (response.data && response.data.success) {
          const { capacityCounts } = response.data.data;

          const map = { 2: 0, 4: 0, 6: 0 }; // 기본 틀 구성
          (capacityCounts || []).forEach((item) => {
            map[item.capacity] = item.count;
          });
          setCapacityMap(map);
        }
      } catch (error) {
        console.error('테이블 요약 정보 조회 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchTableSummary();
  }, []);

  // 인원수별 수량 변경
  const handleCapacityChange = (cap, val) => {
    const num = Math.max(0, parseInt(val, 10) || 0);
    setCapacityMap((prev) => ({
      ...prev,
      [cap]: num,
    }));
  };

  // 총 운영 테이블 개수 계산
  const totalTablesCount = Object.values(capacityMap).reduce(
    (acc, cur) => acc + Number(cur),
    0,
  );

  // 저장 로직 (테이블 구성 저장 -> 방문 예약 정책 저장)
  const handleSaveSettings = async () => {
    if (isSaving) return;
    setIsSaving(true);

    try {
      const tablesPayload = {
        tables: Object.entries(capacityMap).map(([capacity, count]) => ({
          capacity: Number(capacity),
          count: Number(count),
        })),
      };

      const tableRes = await reservationApi.saveStoreTables(tablesPayload);
      if (!tableRes.data || !tableRes.data.success) {
        throw new Error('테이블 구성 저장에 실패했습니다.');
      }

      const settingsPayload = {
        enabled: isEnabled,
        sameDayReservationAllowed: sameDayReservationAllowed,
        slotIntervalMinutes: Number(slotIntervalMinutes),
        cancelDeadlineMinutes: initialSettings?.cancelDeadlineMinutes || 0,
        startTime: initialSettings?.startTime || '10:00',
        endTime: initialSettings?.endTime || '20:00',
        table2Seater: capacityMap[2] || 0,
        table4Seater: capacityMap[4] || 0,
        table6Seater: capacityMap[6] || 0,
        defaultMaxTeamCount: totalTablesCount,
      };

      const settingsRes =
        await reservationApi.updateVisitSettings(settingsPayload);
      if (settingsRes.data && settingsRes.data.success) {
        alert('테이블 구성 및 방문 예약 정책이 저장되었습니다.');
        if (refreshSettings) await refreshSettings();
        onClose();
      }
    } catch (error) {
      console.error('설정 저장 중 오류 발생:', error);
      alert(error.message || '설정 저장 도중 오류가 발생했습니다.');
    } finally {
      setIsSaving(false);
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

          {/* 예약 타임 간격 */}
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

          {/* 슬롯당 최대 수용량 (테이블 구성) */}
          <FormGroup>
            <GroupTitle>
              <Layers size={15} color="#4CA771" />
              <span>슬롯당 최대 수용량 (인원석별 테이블 수)</span>
            </GroupTitle>

            {isLoading ? (
              <LoadingText>테이블 요약 정보를 불러오는 중...</LoadingText>
            ) : (
              Object.entries(capacityMap).map(([capacity, count]) => (
                <InputRow key={capacity}>
                  <label>{capacity}인용 테이블 제한 수</label>
                  <FieldControl>
                    <StyledInput
                      type="number"
                      min="0"
                      value={count}
                      onChange={(e) =>
                        handleCapacityChange(capacity, e.target.value)
                      }
                    />
                    <span>개</span>
                  </FieldControl>
                </InputRow>
              ))
            )}

            <TotalSummary>
              타임당 총 운영 테이블: {totalTablesCount}개
            </TotalSummary>
          </FormGroup>
        </ScrollArea>

        <FooterGrid>
          <FooterBtn onClick={onClose} disabled={isSaving}>
            취소
          </FooterBtn>
          <FooterBtn $primary onClick={handleSaveSettings} disabled={isSaving}>
            {isSaving ? '저장 중...' : '저장'}
          </FooterBtn>
        </FooterGrid>
      </ModalContainer>
    </Overlay>
  );
}
