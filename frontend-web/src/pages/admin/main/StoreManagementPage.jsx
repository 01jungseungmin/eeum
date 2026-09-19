import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import StoreFilterBar from '../../../components/admin/store/StoreFilterBar';
import StoreTable from '../../../components/admin/store/StoreTable';
import { storeApi } from '../../../api/admin/storeApi';

const Container = styled.div`
  padding: 30px;
`;

const PaginationContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
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
      }
    } catch (error) {
      console.error('상점 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page, keyword, status]);

  useEffect(() => {
    queueMicrotask(() => fetchStores());
  }, [fetchStores]);

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
      <StoreFilterBar
        keyword={keyword}
        onSearch={handleSearch}
        status={status}
        onStatusChange={handleStatusChange}
      />

      <StoreTable
        stores={stores}
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
