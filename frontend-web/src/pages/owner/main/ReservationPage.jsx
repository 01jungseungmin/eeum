import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import {
  ChevronLeft,
  ChevronRight,
  Sliders,
  CalendarRange,
} from 'lucide-react';
import SummaryCard from '../../../components/owner/reservation/SummaryCard';
import TimeSlotStatus from '../../../components/owner/reservation/TimeSlotStatus';
import ReservationList from '../../../components/owner/reservation/ReservationList';
import CapacityModal from '../../../components/owner/reservation/CapacityModal';
import DateSlotEditModal from '../../../components/owner/reservation/DateSlotEditModal';
import { reservationApi } from '../../../api/owner/reservationApi';

const PageContainer = styled.div`
  padding: 24px;
  background-color: #f8f9fa;
  min-height: 100vh;
  font-family: 'Noto Sans KR', sans-serif;
`;

const HeaderZone = styled.div`
  margin-bottom: 24px;
  h2 {
    font-size: 20px;
    font-weight: 700;
    color: #1a1a1a;
    margin-bottom: 4px;
  }
  p {
    font-size: 13px;
    color: #868e96;
  }
`;

// 상단 요약본 대시보드 4열 배치
const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
`;

// 대시보드 메인 본문 2열 (좌측 대시보드 서브컨트롤 / 우측 실시간 리스트)
const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 24px;
  align-items: start;
`;

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

// 캘린더 스타일 컴포넌트
const CalendarCard = styled.div`
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e9ecef;
`;

const CalendarHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h4 {
    font-size: 14px;
    font-weight: 700;
    color: #333;
    text-align: center;
    span {
      display: block;
      font-size: 11px;
      color: #999;
      margin-top: 2px;
    }
  }

  button {
    background: none;
    border: none;
    cursor: pointer;
    color: #868e96;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px;
    &:hover {
      color: #333;
    }
  }
`;

const WeekDays = styled.div`
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #adb5bd;
  margin-bottom: 12px;
`;

const DaysGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
`;

const DayButton = styled.button`
  background: ${(props) => (props.$active ? '#4CA771' : 'none')};
  border: none;
  border-radius: 8px;
  height: 38px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;

  color: ${(props) => {
    if (props.$active) return '#fff';
    if (props.$isSunday) return '#e03131';
    if (props.$isSaturday) return '#1971c2';
    return '#495057';
  }};

  &:hover {
    background: ${(props) => (props.$active ? '#4CA771' : '#f1f3f5')};
  }

  &::after {
    content: '';
    display: ${(props) => (props.$isToday ? 'block' : 'none')};
    position: absolute;
    bottom: 4px;
    width: 4px;
    height: 4px;
    background: ${(props) => (props.$active ? '#fff' : '#4CA771')};
    border-radius: 50%;
  }
`;

const ModalTriggerButton = styled.button`
  width: 100%;
  background: #fff;
  border: 1px dashed #4ca771;
  color: #4ca771;
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;

  &:hover {
    background: #f4fbf7;
  }
`;

const DateEditTriggerButton = styled(ModalTriggerButton)`
  border: 1px dashed #4c6ef5;
  color: #4c6ef5;
  &:hover {
    background: #f0f4ff;
  }
`;

const formatDateString = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

export default function ReservationPage() {
  const [isCapacityModalOpen, setIsCapacityModalOpen] = useState(false);
  const [isDateModalOpen, setIsDateModalOpen] = useState(false);

  const todayObj = new Date();
  const todayFormatted = formatDateString(todayObj);

  const [currentWeekMonday, setCurrentWeekMonday] = useState(() => {
    const currentDay = todayObj.getDay();
    const diff = todayObj.getDate() - currentDay + (currentDay === 0 ? -6 : 1);
    const targetMonday = new Date(todayObj);
    targetMonday.setDate(diff);
    return targetMonday;
  });

  const [selectedDate, setSelectedDate] = useState(todayFormatted);
  const [settings, setSettings] = useState(null);
  const [timeSlots, setTimeSlots] = useState([]);
  const [orders, setOrders] = useState([]);
  const [filter, setFilter] = useState('전체');
  const [isOrdersLoading, setIsOrdersLoading] = useState(false);

  // 기본 설정과 시간대 현황 조회
  const fetchSettings = async () => {
    try {
      const response = await reservationApi.getVisitSettings();
      if (response.data && response.data.success) {
        setSettings({ ...response.data.data });
        fetchTimeSlots(selectedDate);
      }
    } catch (error) {
      console.error('기본 설정 로드 실패:', error);
    }
  };

  // 특정 날짜의 시간대 현황 조회
  const fetchTimeSlots = useCallback(async (date) => {
    try {
      const response = await reservationApi.getDateTimeSlots(date);
      if (response.data && response.data.success) {
        setTimeSlots(
          Array.isArray(response.data.data) ? response.data.data : [],
        );
      }
    } catch (error) {
      console.error('시간대 현황 로드 실패:', error);
    }
  }, []);

  // 특정 날짜의 예약 목록 조회
  const fetchOrders = useCallback(async () => {
    setIsOrdersLoading(true);
    try {
      let status = undefined;
      if (filter === '확정') status = 'APPROVED';
      if (filter === '대기') status = 'PENDING';
      if (filter === '취소') status = 'CANCELED';

      const response = await reservationApi.getVisitReservations({ status });
      if (response.data && response.data.success) {
        console.log('주문 목록 로드 성공:', response.data.data?.content || []);
        setOrders(response.data.data?.content || []);
      }
    } catch (error) {
      console.error('주문 목록 로드 실패:', error);
      setOrders([]);
    } finally {
      setIsOrdersLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    fetchSettings();
  }, []);

  useEffect(() => {
    fetchOrders();
    fetchTimeSlots(selectedDate);
  }, [selectedDate, fetchOrders, fetchTimeSlots]);

  const filteredOrders = orders.filter(
    (order) => order.visitDate === selectedDate,
  );

  const totalCount = filteredOrders.length;
  const confirmedCount = filteredOrders.filter(
    (o) => o.status === 'APPROVED' || o.status === 'CONFIRMED',
  ).length;
  const pendingCount = filteredOrders.filter(
    (o) => o.status === 'PENDING',
  ).length;

  // 주간 달력용 날짜 코드
  const weekDaysList = Array.from({ length: 7 }).map((_, index) => {
    const day = new Date(currentWeekMonday);
    day.setDate(currentWeekMonday.getDate() + index);
    return day;
  });

  const handlePrevWeek = () => {
    const nextDate = new Date(currentWeekMonday);
    nextDate.setDate(currentWeekMonday.getDate() - 7);
    setCurrentWeekMonday(nextDate);
  };

  const handleNextWeek = () => {
    const nextDate = new Date(currentWeekMonday);
    nextDate.setDate(currentWeekMonday.getDate() + 7);
    setCurrentWeekMonday(nextDate);
  };

  const startDayText = `${weekDaysList[0].getMonth() + 1}월 ${weekDaysList[0].getDate()}일`;
  const endDayText = `${weekDaysList[6].getMonth() + 1}월 ${weekDaysList[6].getDate()}일`;
  const currentYear = weekDaysList[0].getFullYear();

  return (
    <PageContainer>
      <SummaryGrid>
        <SummaryCard
          title="선택일 전체 예약"
          count={totalCount}
          color="#4CA771"
        />
        <SummaryCard title="확정" count={confirmedCount} color="#1a1a1a" />
        <SummaryCard title="대기" count={pendingCount} color="#fab005" />
        <SummaryCard title="시간대" count={timeSlots.length} color="#4c6ef5" />
      </SummaryGrid>

      <ContentGrid>
        <LeftSection>
          <CalendarCard>
            <CalendarHeader>
              <button onClick={handlePrevWeek}>
                <ChevronLeft size={18} />
              </button>
              <h4>
                {startDayText} — {endDayText}
                <span>{currentYear}년</span>
              </h4>
              <button onClick={handleNextWeek}>
                <ChevronRight size={18} />
              </button>
            </CalendarHeader>
            <WeekDays>
              <div>월</div>
              <div>화</div>
              <div>수</div>
              <div>목</div>
              <div>금</div>
              <div style={{ color: '#1971c2' }}>토</div>
              <div style={{ color: '#e03131' }}>일</div>
            </WeekDays>
            <DaysGrid>
              {weekDaysList.map((dateObj, index) => {
                const formatted = formatDateString(dateObj);
                const isSelected = selectedDate === formatted;
                const isSunday = dateObj.getDay() === 0;
                const isSaturday = dateObj.getDay() === 6;
                const isToday = formatted === todayFormatted;

                return (
                  <DayButton
                    key={index}
                    $active={isSelected}
                    $isSunday={isSunday}
                    $isSaturday={isSaturday}
                    $isToday={isToday}
                    onClick={() => setSelectedDate(formatted)}
                  >
                    {dateObj.getDate()}
                  </DayButton>
                );
              })}
            </DaysGrid>
          </CalendarCard>

          {/* 가게 공통 기본 설정용 모달 버튼 */}
          <ModalTriggerButton onClick={() => setIsCapacityModalOpen(true)}>
            <Sliders size={16} /> 시간대별 수용인원 설정
          </ModalTriggerButton>

          {/* 이 버튼을 눌렀을 때만 하루 전용 조절 모달(DateSlotEditModal)이 활성화 */}
          <DateEditTriggerButton onClick={() => setIsDateModalOpen(true)}>
            <CalendarRange size={16} /> 🗓️ 특정 일자 시간대 오픈/차단 조정
          </DateEditTriggerButton>

          {/* 클릭 연동 코드를 완전히 삭제하여 순수 리스트 뷰어로만 쓰이도록 격리 */}
          <TimeSlotStatus
            slotsData={timeSlots}
            selectedDate={selectedDate}
            dayOrders={filteredOrders}
            defaultSettings={settings}
          />
        </LeftSection>

        <ReservationList
          selectedDate={selectedDate}
          orders={filteredOrders}
          loading={isOrdersLoading}
          filter={filter}
          setFilter={setFilter}
          refreshOrders={fetchOrders}
          refreshTimeSlots={fetchTimeSlots}
        />
      </ContentGrid>

      {/* 가게 기본 정책 모달 */}
      {isCapacityModalOpen && (
        <CapacityModal
          initialSettings={settings}
          onClose={() => setIsCapacityModalOpen(false)}
          refreshSettings={fetchSettings}
        />
      )}

      {/* 특정 일자 전용 시간대 수정 모달 (새로운 전용 버튼으로만 작동) */}
      {isDateModalOpen && (
        <DateSlotEditModal
          selectedDate={selectedDate}
          onClose={() => setIsDateModalOpen(false)}
          refreshDashboard={() => fetchTimeSlots(selectedDate)}
        />
      )}
    </PageContainer>
  );
}
