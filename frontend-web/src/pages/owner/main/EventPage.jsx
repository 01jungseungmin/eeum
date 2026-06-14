// src/pages/owner/event/EventPage.jsx
import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Plus } from 'lucide-react';

import EventStats from '../../../components/owner/event/EventStats';
import EventAlertBanner from '../../../components/owner/event/EventAlertBanner';
import EventItemRow from '../../../components/owner/event/EventItemRow';
import EventModal from '../../../components/owner/event/EventModal';

import { eventApi } from '../../../api/owner/eventApi';

const PageContainer = styled.div`
  padding: 24px;
  background: #f8f9fa;
  min-height: 100vh;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const MainCard = styled.div`
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  border: 1px solid #eef0f2;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h2 {
    font-size: 15px;
    font-weight: bold;
    color: #1a1f2c;
    margin: 0;
  }
`;

const AddButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background: #00a651;
  color: white;
  border: none;
  padding: 10px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  &:hover {
    background: #008f45;
  }
`;

const EventList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const INITIAL_EVENTS = [
  {
    id: 1,
    productId: 101,
    name: '오늘의 특가! 불고기 반찬 (300g)',
    emoji: '🥩',
    originalPrice: 12000,
    discountedPrice: 8900,
    discountRate: 26,
    status: 'LIVE',
    startDate: '2026-06-11T12:00',
    endDate: '2026-06-11T18:00',
    currentSales: 18,
    totalQuantity: 30,
  },
  {
    id: 2,
    productId: 103,
    name: '한정수량! 잡채 세트 (400g)',
    emoji: '🍲',
    originalPrice: 10000,
    discountedPrice: 7500,
    discountRate: 25,
    status: 'READY',
    startDate: '2026-06-11T18:00',
    endDate: '2026-06-11T21:00',
    currentSales: 0,
    totalQuantity: 20,
  },
  {
    id: 3,
    productId: 104,
    name: '마감 세일! 시금치나물 (150g)',
    emoji: '🥬',
    originalPrice: 3500,
    discountedPrice: 2000,
    discountRate: 43,
    status: 'DONE',
    startDate: '2024-04-27T17:00',
    endDate: '2024-04-27T20:00',
    currentSales: 15,
    totalQuantity: 15,
  },
];

function EventPage() {
  const [events, setEvents] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedEvent, setSelectedEvent] = useState(null);

  const loadEventList = async () => {
    setIsLoading(true);
    try {
      const response = await eventApi.getOwnerEventProducts();
      if (response.data && response.data.success) {
        console.log(response.data.data);
        setEvents(response.data.data);
      }
    } catch (error) {
      console.error('이벤트 목록 로드 에러:', error);
      alert('이벤트 상품 목록을 불러오는 도중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadEventList();
  }, []);

  // 대시보드 상태값 연산 핸들링
  const liveCount = events.filter((e) => e.status === 'ONGOING').length;
  const readyCount = events.filter((e) => e.status === 'READY').length;
  const totalCount = events.length;

  const handleCreateButtonClick = () => {
    setSelectedEvent(null); // ✨ 중요: 수정 중이던 데이터 흔적을 지워줌 (등록 모드로 전환)
    setIsModalOpen(true);
  };

  // ➕ [추가] 2. 목록에서 '연필 버튼'을 눌렀을 때 핸들러
  const handleEditButtonClick = (eventItem) => {
    setSelectedEvent(eventItem); // ✨ 중요: 클릭한 행의 이벤트 정보를 상태에 주입 (수정 모드로 전환)
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (eventItem) => {
    setSelectedEvent(eventItem);
    setIsModalOpen(true);
  };

  const handleModalSubmit = async (formData) => {
    try {
      if (selectedEvent) {
        // [수정 모드]
        const eventProductId = selectedEvent.eventProductId;
        const response = await eventApi.updateOwnerEventProduct(
          eventProductId,
          formData,
        );
        if (response.data?.success) {
          alert('이벤트 정보가 성공적으로 수정되었습니다.');
          loadEventList();
        }
      } else {
        // [등록 모드]
        const response = await eventApi.createOwnerEventProduct(formData);
        if (response.data?.success) {
          alert('새 이벤트가 성공적으로 등록되었습니다.');
          loadEventList();
        }
      }
    } catch (error) {
      console.error(error);
    } finally {
      setIsModalOpen(false);
      setSelectedEvent(null); // 모달이 닫힐 때 상태 초기화
    }
  };

  const handleDeleteEvent = async (id, name) => {
    if (window.confirm(`[${name}] 이벤트를 취소/삭제하시겠습니까?`)) {
      try {
        // 나중에 eventApi.deleteOwnerEventProduct(id) 호출 영역
        setEvents(events.filter((e) => e.eventProductId !== id)); // Swagger ID 기준 필터링
      } catch (error) {
        alert('삭제에 실패했습니다.');
      }
    }
  };

  return (
    <PageContainer>
      <EventStats
        liveCount={liveCount}
        readyCount={readyCount}
        totalCount={totalCount}
      />

      <EventAlertBanner />

      <MainCard>
        <CardHeader>
          <h2>이벤트 상품 목록 ({events.length}개)</h2>
          <AddButton onClick={handleCreateButtonClick}>
            <Plus size={16} strokeWidth={2.5} /> 이벤트 등록
          </AddButton>
        </CardHeader>

        <EventList>
          {events.map((evt, index) => (
            <EventItemRow
              key={evt.eventProductId || evt.id || `event-${index}`}
              evt={evt}
              onEdit={handleOpenEditModal}
              onDelete={handleDeleteEvent}
            />
          ))}
        </EventList>
      </MainCard>

      <EventModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={handleModalSubmit}
        editData={selectedEvent}
      />
    </PageContainer>
  );
}

export default EventPage;
