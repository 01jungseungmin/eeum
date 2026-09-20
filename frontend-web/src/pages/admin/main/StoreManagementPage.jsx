import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { Store, CheckCircle2, PauseCircle, Ban } from 'lucide-react';
import StoreFilterBar from '../../../components/admin/store/StoreFilterBar';
import StoreTable from '../../../components/admin/store/StoreTable';
import { storeApi } from '../../../api/admin/storeApi';
import { clickableCardStyle } from '../../../components/common/cardFilterStyle';

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

  ${clickableCardStyle}
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
  transition: all 0.15s;

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

const PAGE_SIZE = 10;

function StoreManagementPage() {
  const [stores, setStores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  // 상단 카드로 고르는 보기 필터 (현재 불러온 페이지 안에서만 적용): ALL | OPEN | PAUSED | CLOSED
  const [cardFilter, setCardFilter] = useState('ALL');

  const fetchStores = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: PAGE_SIZE };
      if (keyword) params.keyword = keyword;
      if (status) params.status = status;

      const res = await storeApi.getStores(params);
      if (res.data?.success) {
        setStores(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
        setTotalElements(res.data.data.totalElements || 0);
      }
    } catch (error) {
      console.error('상점 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page, keyword, status]);

  // 상태별 집계 API가 따로 없어 "이 페이지" 범위로만 계산한다.
  const pageStats = useMemo(() => {
    return {
      open: stores.filter((s) => s.status === 'OPEN').length,
      paused: stores.filter(
        (s) => s.status === 'TEMP_CLOSED' || s.status === 'SUSPENDED',
      ).length,
      closed: stores.filter((s) => s.status === 'CLOSED').length,
    };
  }, [stores]);

  useEffect(() => {
    queueMicrotask(() => fetchStores());
  }, [fetchStores]);

  const displayedStores = useMemo(() => {
    if (cardFilter === 'OPEN') return stores.filter((s) => s.status === 'OPEN');
    if (cardFilter === 'PAUSED') {
      return stores.filter(
        (s) => s.status === 'TEMP_CLOSED' || s.status === 'SUSPENDED',
      );
    }
    if (cardFilter === 'CLOSED') {
      return stores.filter((s) => s.status === 'CLOSED');
    }
    return stores;
  }, [stores, cardFilter]);

  const handleSearch = (nextKeyword) => {
    setKeyword(nextKeyword);
    setPage(0);
  };

  const handleStatusChange = (nextStatus) => {
    setStatus(nextStatus);
    setPage(0);
  };

  return (
    <Container>
      <SummaryGrid>
        <SummaryCard
          $iconBg="#f0f5ff"
          $iconColor="#2f54eb"
          $clickable
          $active={cardFilter === 'ALL'}
          onClick={() => setCardFilter('ALL')}
        >
          <div className="info">
            <span>전체 상점</span>
            <h2>{totalElements.toLocaleString()}</h2>
            <p>전체 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <Store size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#edf5f1"
          $iconColor="#2d5a43"
          $clickable
          $active={cardFilter === 'OPEN'}
          onClick={() => setCardFilter('OPEN')}
        >
          <div className="info">
            <span>영업중</span>
            <h2>{pageStats.open}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <CheckCircle2 size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#ad6800"
          $clickable
          $active={cardFilter === 'PAUSED'}
          onClick={() => setCardFilter('PAUSED')}
        >
          <div className="info">
            <span>휴업 / 정지</span>
            <h2>{pageStats.paused}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <PauseCircle size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fff1f0"
          $iconColor="#f5222d"
          $clickable
          $active={cardFilter === 'CLOSED'}
          onClick={() => setCardFilter('CLOSED')}
        >
          <div className="info">
            <span>폐업</span>
            <h2>{pageStats.closed}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <Ban size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <StoreFilterBar
        keyword={keyword}
        onSearch={handleSearch}
        status={status}
        onStatusChange={handleStatusChange}
      />

      <StoreTable
        stores={displayedStores}
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

export default StoreManagementPage;
