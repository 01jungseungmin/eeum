import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import OperationSummaryCards from '../../../components/admin/operation/OperationSummaryCards';
import OperationFailureFilterBar from '../../../components/admin/operation/OperationFailureFilterBar';
import OperationFailureTable from '../../../components/admin/operation/OperationFailureTable';
import { operationApi } from '../../../api/admin/operationApi';
import { OPERATION_SUMMARY_HOURS_OPTIONS } from '../../../constants/operationConstants';

const Container = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const SummaryHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const PageTitle = styled.h1`
  font-size: 20px;
  font-weight: 700;
  color: #262626;
  margin: 0;
`;

const HoursSelect = styled.select`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 14px;
  height: 36px;
  font-size: 13px;
  background: white;
  cursor: pointer;
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

function OperationMonitoringPage() {
  const [summary, setSummary] = useState(null);
  const [hours, setHours] = useState(24);

  const [failures, setFailures] = useState([]);
  const [loading, setLoading] = useState(true);
  const [category, setCategory] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const fetchSummary = useCallback(async () => {
    try {
      const res = await operationApi.getSummary(hours);
      if (res.data?.success) {
        setSummary(res.data.data);
      }
    } catch (error) {
      console.error('운영 현황 요약 조회 실패:', error);
    }
  }, [hours]);

  const fetchFailures = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: PAGE_SIZE };
      if (category) params.category = category;
      if (keyword.trim()) params.keyword = keyword.trim();

      const res = await operationApi.getFailures(params);
      if (res.data?.success) {
        setFailures(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
      }
    } catch (error) {
      console.error('운영 실패 이력 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page, category, keyword]);

  useEffect(() => {
    queueMicrotask(() => fetchSummary());
  }, [fetchSummary]);

  useEffect(() => {
    queueMicrotask(() => fetchFailures());
  }, [fetchFailures]);

  const handleCategoryChange = (next) => {
    setCategory(next);
    setPage(0);
  };

  const handleKeywordChange = (next) => {
    setKeyword(next);
    setPage(0);
  };

  return (
    <Container>
      <SummaryHeader>
        <PageTitle>운영 모니터링</PageTitle>
        <HoursSelect
          value={hours}
          onChange={(e) => setHours(Number(e.target.value))}
        >
          {OPERATION_SUMMARY_HOURS_OPTIONS.map((opt) => (
            <option
              key={opt.value}
              value={opt.value}
            >
              {opt.label}
            </option>
          ))}
        </HoursSelect>
      </SummaryHeader>

      <OperationSummaryCards summary={summary} />

      <div>
        <OperationFailureFilterBar
          category={category}
          onCategoryChange={handleCategoryChange}
          keyword={keyword}
          onKeywordChange={handleKeywordChange}
        />
        <OperationFailureTable
          failures={failures}
          loading={loading}
        />
      </div>

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

export default OperationMonitoringPage;
