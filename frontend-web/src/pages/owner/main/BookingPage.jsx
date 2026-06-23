import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { ChevronLeft, ChevronRight, Sliders } from 'lucide-react';
import SummaryCard from '../../../components/owner/booking/SummaryCard';
import TimeSlotStatus from '../../../components/owner/booking/TimeSlotStatus';
import ReservationList from '../../../components/owner/booking/ReservationList';
import CapacityModal from '../../../components/owner/booking/CapacityModal';
import { bookingApi } from '../../../api/owner/bookingApi';

const PageContainer = styled.div`
  padding: 24px;
  background-color: #f8f9fa;
  min-height: 100vh;
  font-family: 'Noto Sans KR', sans-serif;
`;

const Header = styled.div`
  margin-bottom: 24px;
  h1 {
    font-size: 20px;
    font-weight: 700;
    color: #1a1a1a;
    margin-bottom: 4px;
  }
  p {
    font-size: 13px;
    color: #666;
  }
`;

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 24px;
  align-items: start;
`;

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const CalendarCard = styled.div`
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e9ecef;
  opacity: ${(props) => (props.$enabled ? 1 : 0.5)};
`;

const CalendarHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h2 {
    font-size: 15px;
    font-weight: 700;
    text-align: center;
    span {
      display: block;
      font-size: 11px;
      color: #999;
      font-weight: 400;
    }
  }
  button {
    background: none;
    border: none;
    cursor: pointer;
    color: #666;
    padding: 4px;
  }
`;

const WeekDays = styled.div`
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 12px;
  color: #666;
  margin-bottom: 12px;
`;

const DaysGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  gap: 8px;
`;

const DayButton = styled.button`
  background: ${(props) => (props.$active ? '#4CA771' : 'none')};
  color: ${(props) =>
    props.$active
      ? '#fff'
      : props.$isSunday
        ? '#e03131'
        : props.$isSaturday
          ? '#1971c2'
          : '#1a1a1a'};
  border: none;
  border-radius: 8px;
  height: 40px;
  cursor: pointer;
  font-weight: ${(props) => (props.$active ? '700' : '400')};
  position: relative;

  &:hover {
    background: ${(props) => (props.$active ? '#4CA771' : '#f1f3f5')};
  }

  ${(props) =>
    props.$isToday &&
    `
    &::after {
      content: '';
      position: absolute;
      bottom: 4px;
      left: 50%;
      transform: translateX(-50%);
      width: 4px;
      height: 4px;
      background-color: ${props.$active ? '#fff' : '#4CA771'};
      border-radius: 50%;
    }
  `}
`;

const ModalTriggerButton = styled.button`
  width: 100%;
  background: #fff;
  border: 1px dashed #4ca771;
  color: #4ca771;
  padding: 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  &:hover {
    background: #f4fbf7;
  }
`;

const formatDateString = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

export default function BookingPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  // [수정] 실제 시스템의 오늘 날짜(2026-06-21) 확보 및 포맷화
  const todayObj = new Date();
  const todayFormatted = formatDateString(todayObj);

  // 기준 주차의 월요일 구하기 (오늘 날짜 기준으로 동적 계산)
  const [currentWeekMonday, setCurrentWeekMonday] = useState(() => {
    const currentDay = todayObj.getDay();
    // 일요일(0)이면 전달 월요일로 갈 수 있게 -6, 그 외 요일은 월요일과의 차이 계산
    const diff = todayObj.getDate() - currentDay + (currentDay === 0 ? -6 : 1);
    const targetMonday = new Date(todayObj);
    targetMonday.setDate(diff);
    return targetMonday;
  });

  // 초기 활성화 선택 날짜를 '오늘 날짜'로 연동
  const [selectedDate, setSelectedDate] = useState(todayFormatted);

  const [settings, setSettings] = useState(null);
  const [timeSlots, setTimeSlots] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  const weekDaysList = Array.from({ length: 7 }).map((_, index) => {
    const day = new Date(currentWeekMonday);
    day.setDate(currentWeekMonday.getDate() + index);
    return day;
  });

  // 전 주
  const handlePrevWeek = () => {
    const nextDate = new Date(currentWeekMonday);
    nextDate.setDate(currentWeekMonday.getDate() - 7);
    setCurrentWeekMonday(nextDate);
  };

  // 다음 주
  const handleNextWeek = () => {
    const nextDate = new Date(currentWeekMonday);
    nextDate.setDate(currentWeekMonday.getDate() + 7);
    setCurrentWeekMonday(nextDate);
  };

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const response = await bookingApi.getVisitSettings();
        if (response.data && response.data.success) {
          setSettings(response.data.data);
        }
      } catch (error) {
        console.error('기본 설정을 불러오는데 실패했습니다.', error);
      }
    };
    fetchSettings();
  }, []);

  const fetchTimeSlots = useCallback(async (date) => {
    try {
      const response = await bookingApi.getVisitTimeSlots(date);
      if (response.data && response.data.success) {
        setTimeSlots(response.data.data);
      }
    } catch (error) {
      console.error('시간대별 현황 조회 실패:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTimeSlots(selectedDate);
  }, [selectedDate, fetchTimeSlots]);

  const startDayText = `${weekDaysList[0].getMonth() + 1}월 ${weekDaysList[0].getDate()}일`;
  const endDayText = `${weekDaysList[6].getMonth() + 1}월 ${weekDaysList[6].getDate()}일`;
  const currentYear = weekDaysList[0].getFullYear();

  if (isLoading) return <PageContainer>데이터 로딩 중...</PageContainer>;

  return (
    <PageContainer>
      <SummaryGrid>
        <SummaryCard title="오늘 전체 예약" count={6} color="#4CA771" />
        <SummaryCard title="확정" count={4} color="#1a1a1a" />
        <SummaryCard title="대기" count={2} color="#fab005" />
        <SummaryCard title="시간대" count={timeSlots.length} color="#4c6ef5" />
      </SummaryGrid>

      <ContentGrid>
        <LeftSection>
          <CalendarCard $enabled={settings?.enabled}>
            <CalendarHeader>
              <button onClick={handlePrevWeek}>
                <ChevronLeft size={18} />
              </button>
              <h2>
                {startDayText} — {endDayText}
                <span>{currentYear}년</span>
              </h2>
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

                // [변경 포인트] 고정 문자열이 아닌 실제 오늘 날짜와 대조하여 초록 점 생성
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

          <ModalTriggerButton onClick={() => setIsModalOpen(true)}>
            <Sliders size={16} />
            시간대별 수용인원 설정
          </ModalTriggerButton>

          <TimeSlotStatus slotsData={timeSlots} selectedDate={selectedDate} />
        </LeftSection>

        <ReservationList selectedDate={selectedDate} />
      </ContentGrid>

      {isModalOpen && (
        <CapacityModal
          slotsData={timeSlots}
          selectedDate={selectedDate}
          onClose={() => setIsModalOpen(false)}
          refreshData={() => fetchTimeSlots(selectedDate)}
        />
      )}
    </PageContainer>
  );
}
