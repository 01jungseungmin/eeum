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

const FilterTabGroup = styled.div`
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
`;

const FilterButton = styled.button`
  background: ${(props) => (props.$active ? '#1a1f2c' : '#ffffff')};
  color: ${(props) => (props.$active ? '#ffffff' : '#8e94a0')};
  border: 1px solid ${(props) => (props.$active ? '#1a1f2c' : '#eef0f2')};
  padding: 8px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${(props) => (props.$active ? '#1a1f2c' : '#f8f9fa')};
  }
`;

function EventPage() {
  const [events, setEvents] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedEvent, setSelectedEvent] = useState(null);

  const [filterStatus, setFilterStatus] = useState('ALL');

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
  const liveCount = events.filter((e) => e.eventStatus === 'ONGOING').length;
  const readyCount = events.filter((e) => e.eventStatus === 'SCHEDULED').length;
  const totalCount = events.length;

  const getFilteredEvents = () => {
    return events.filter((evt) => {
      if (filterStatus === 'ALL') return true;

      const remainingStock = evt?.remainingStock || 0;
      const isOut = remainingStock <= 0;

      if (filterStatus === 'LIVE') {
        return evt.eventStatus === 'ONGOING' && !isOut;
      }
      if (filterStatus === 'READY') {
        return evt.eventStatus === 'SCHEDULED';
      }
      if (filterStatus === 'DONE') {
        return evt.eventStatus === 'ENDED' || isOut;
      }
      return true;
    });
  };

  const filteredEvents = getFilteredEvents();

  const handleCreateButtonClick = () => {
    setSelectedEvent(null);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (eventItem) => {
    setSelectedEvent(eventItem);
    setIsModalOpen(true);
  };

  const handleModalSubmit = async (formData) => {
    try {
      if (selectedEvent) {
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
        const now = new Date();

        const isAlreadyExist = events.some((evt) => {
          if (Number(evt.productId) !== Number(formData.productId))
            return false;

          const startAt = evt.startAt ? new Date(evt.startAt) : null;
          const endAt = evt.endAt ? new Date(evt.endAt) : null;
          const remainingStock = evt.remainingStock || 0;

          // 이미 종료 날짜가 지나버린 당일 이벤트는 검사에서 제외
          if (endAt && endAt < now) return false;
          if (remainingStock <= 0) return false;

          const isLive = startAt && endAt && now >= startAt && now <= endAt;
          const isReady = startAt && now < startAt;

          return isLive || isReady;
        });

        // 중복 방어
        if (isAlreadyExist) {
          alert(
            '해당 상품은 이미 진행 중이거나 진행 예정인 이벤트가 존재합니다.\n동일 상품에 대한 중복 이벤트 등록은 불가능합니다.',
          );
          return;
        }

        // 이벤트 등록
        const response = await eventApi.createOwnerEventProduct(formData);
        if (response.data?.success) {
          alert('새 이벤트가 성공적으로 등록되었습니다.');
          loadEventList();
        }
      }

      setIsModalOpen(false);
      setSelectedEvent(null);
    } catch (error) {
      console.error('이벤트 처리 중 에러 발생:', error);

      const serverError = error.response?.data?.error;
      const errorCode = serverError?.code;
      const serverMessage = serverError?.message;

      if (errorCode) {
        // 백엔드 에러 코드별 세부 분기 및 안내 문구 매핑
        switch (errorCode) {
          case 'EVENT_001':
            alert('존재하지 않거나 이미 삭제된 이벤트 상품입니다.');
            break;
          case 'EVENT_002':
            alert('현재 진행 중인 활성화 이벤트가 아닙니다.');
            break;
          case 'EVENT_003':
            alert('준비된 이벤트 수량이 부족하거나 이미 마감되었습니다.');
            break;
          case 'EVENT_004':
            alert(
              '해당 상품은 이미 활성화된(진행중/진행예정) 이벤트가 존재합니다.\n중복 등록이 불가능합니다.',
            );
            break;
          case 'EVENT_005':
            alert(
              '이벤트 기간 설정 오류:\n시작 시간은 종료 시간보다 빨라야 합니다.',
            );
            break;
          case 'EVENT_006':
            alert(
              '이벤트 가격 설정 오류:\n이벤트 가격은 원래 상품의 기본 가격보다 낮아야 합니다.',
            );
            break;
          default:
            alert(serverMessage || '요청 처리 중 오류가 발생했습니다.');
        }
      } else {
        alert('서버와의 통신이 원활하지 않습니다. 잠시 후 다시 시도해 주세요.');
      }
    }
  };

  // 이벤트 삭제
  const handleDeleteEvent = async (eventProductId) => {
    if (
      !window.confirm(
        '정말로 이 이벤트를 삭제하시겠습니까?\n삭제된 이벤트 상품은 사용자 앱 이벤트 목록에서 제외됩니다.',
      )
    ) {
      return;
    }

    try {
      const response = await eventApi.deleteOwnerEventProduct(eventProductId);

      if (response.data?.success) {
        alert('이벤트가 성공적으로 삭제(비활성화)되었습니다.');
        console.log(response.data);
        loadEventList();
      } else {
        alert(response.data?.message || '이벤트 삭제에 실패했습니다.');
      }
    } catch (error) {
      console.error('이벤트 삭제 중 오류 발생:', error);
      alert('이벤트 삭제 처리 중 에러가 발생했습니다.');
    }
  };

  // 조기 종료 핸들러 함수 추가
  const handleEndEvent = async (eventProductId, name) => {
    if (
      !window.confirm(
        `[${name}] 이벤트를 지금 바로 조기 종료하시겠습니까?\n종료된 이벤트는 즉시 소비자 화면에서 내려갑니다.`,
      )
    ) {
      return;
    }
    try {
      const response = await eventApi.endOwnerEventProduct(eventProductId);
      if (response.data?.success) {
        alert('이벤트가 성공적으로 조기 종료되었습니다.');
        loadEventList(); // 목록 새로고침
      }
    } catch (error) {
      console.error('이벤트 조기 종료 중 에러 발생:', error);
      // 이전에 세팅한 백엔드 에러 코드 분기 처리 로직이 있다면 동일하게 활용 가능합니다.
      const serverError = error.response?.data?.error;
      alert(serverError?.message || '조기 종료 처리 중 에러가 발생했습니다.');
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

        <FilterTabGroup>
          <FilterButton
            $active={filterStatus === 'ALL'}
            onClick={() => setFilterStatus('ALL')}
          >
            전체보기
          </FilterButton>
          <FilterButton
            $active={filterStatus === 'LIVE'}
            onClick={() => setFilterStatus('LIVE')}
          >
            진행중 ({liveCount})
          </FilterButton>
          <FilterButton
            $active={filterStatus === 'READY'}
            onClick={() => setFilterStatus('READY')}
          >
            진행 예정 ({readyCount})
          </FilterButton>
          <FilterButton
            $active={filterStatus === 'DONE'}
            onClick={() => setFilterStatus('DONE')}
          >
            종료/매진
          </FilterButton>
        </FilterTabGroup>

        <EventList>
          {filteredEvents.length > 0 ? (
            filteredEvents.map((evt, index) => (
              <EventItemRow
                key={evt.eventProductId || evt.id || `event-${index}`}
                evt={evt}
                onEdit={handleOpenEditModal}
                onDelete={handleDeleteEvent}
                onEnd={handleEndEvent}
              />
            ))
          ) : (
            <div
              style={{
                textAlign: 'center',
                padding: '40px 0',
                color: '#8e94a0',
                fontSize: '14px',
              }}
            >
              해당 조건에 맞는 이벤트 상품이 존재하지 않습니다.
            </div>
          )}
        </EventList>
      </MainCard>

      <EventModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={handleModalSubmit}
        editingEvent={selectedEvent}
      />
    </PageContainer>
  );
}

export default EventPage;
