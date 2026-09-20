import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { MessageSquare, CheckCircle2, XCircle, BellRing } from 'lucide-react';
import ChatRoomFilterBar from '../../../components/admin/chat/ChatRoomFilterBar';
import ChatRoomTable from '../../../components/admin/chat/ChatRoomTable';
import { chatApi } from '../../../api/admin/chatApi';

const Container = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
`;

const SummaryCard = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);

  .info {
    span {
      font-size: 12px;
      color: #8c8c8c;
      font-weight: 500;
    }
    h2 {
      margin: 8px 0 4px 0;
      font-size: 26px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 12px;
      color: #bfbfbf;
      font-weight: 600;
    }
  }
  .icon-wrapper {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: ${(props) => props.$iconBg};
    color: ${(props) => props.$iconColor};
  }
`;

const PaginationContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
`;

const PageButton = styled.button`
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  border: 1px solid ${(props) => (props.$active ? '#2d5a43' : '#d9d9d9')};
  background: ${(props) => (props.$active ? '#2d5a43' : 'white')};
  color: ${(props) => (props.$active ? 'white' : '#555')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;

  &:hover {
    border-color: #2d5a43;
    color: ${(props) => (props.$active ? 'white' : '#2d5a43')};
  }
  &:disabled {
    background: #f5f5f5;
    color: #ccc;
    border-color: #d9d9d9;
    cursor: not-allowed;
  }
`;

const PAGE_SIZE = 20;

function ChatRoomManagementPage() {
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [type, setType] = useState('');
  const [isActive, setIsActive] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const fetchRooms = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: PAGE_SIZE };
      if (type) params.type = type;
      if (isActive) params.isActive = isActive;

      const res = await chatApi.getAllRooms(params);
      if (res.data?.success) {
        setRooms(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
        setTotalElements(res.data.data.totalElements || 0);
      }
    } catch (error) {
      console.error('채팅방 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page, type, isActive]);

  useEffect(() => {
    queueMicrotask(() => fetchRooms());
  }, [fetchRooms]);

  const handleTypeChange = (next) => {
    setType(next);
    setPage(0);
  };
  const handleActiveChange = (next) => {
    setIsActive(next);
    setPage(0);
  };

  // 필터/집계 API가 따로 없어 "이 페이지" 범위로만 계산한다.
  const pageStats = useMemo(() => {
    const activeCount = rooms.filter((r) => r.active).length;
    const totalUnread = rooms.reduce((sum, r) => sum + (r.unreadCount || 0), 0);
    return {
      activeCount,
      inactiveCount: rooms.length - activeCount,
      totalUnread,
    };
  }, [rooms]);

  return (
    <Container>
      <SummaryGrid>
        <SummaryCard
          $iconBg="#f0f5ff"
          $iconColor="#2f54eb"
        >
          <div className="info">
            <span>전체 채팅방</span>
            <h2>{totalElements.toLocaleString()}</h2>
            <p>전체 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <MessageSquare size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#edf5f1"
          $iconColor="#2d5a43"
        >
          <div className="info">
            <span>활성</span>
            <h2>{pageStats.activeCount}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <CheckCircle2 size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#f5f5f5"
          $iconColor="#8c8c8c"
        >
          <div className="info">
            <span>비활성</span>
            <h2>{pageStats.inactiveCount}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <XCircle size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#ad6800"
        >
          <div className="info">
            <span>총 안읽음</span>
            <h2>{pageStats.totalUnread.toLocaleString()}</h2>
            <p>이 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <BellRing size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <ChatRoomFilterBar
        type={type}
        onTypeChange={handleTypeChange}
        isActive={isActive}
        onActiveChange={handleActiveChange}
      />

      <ChatRoomTable
        rooms={rooms}
        loading={loading}
      />

      {totalPages > 1 && (
        <PaginationContainer>
          <PageButton
            disabled={page === 0}
            onClick={() => setPage((prev) => prev - 1)}
          >
            &lt;
          </PageButton>
          {Array.from({ length: totalPages }, (_, index) => (
            <PageButton
              key={index}
              $active={page === index}
              onClick={() => setPage(index)}
            >
              {index + 1}
            </PageButton>
          ))}
          <PageButton
            disabled={page === totalPages - 1}
            onClick={() => setPage((prev) => prev + 1)}
          >
            &gt;
          </PageButton>
        </PaginationContainer>
      )}
    </Container>
  );
}

export default ChatRoomManagementPage;
