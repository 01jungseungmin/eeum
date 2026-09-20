import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import UsedProductTable from '../../../components/admin/used/UsedProductTable';
import { usedProductApi } from '../../../api/admin/usedProductApi';

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

function UsedProductManagementPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const fetchProducts = useCallback(async () => {
    setLoading(true);
    try {
      const res = await usedProductApi.getUsedProducts({
        page,
        size: PAGE_SIZE,
      });
      if (res.data?.success) {
        setProducts(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
        setTotalElements(res.data.data.totalElements || 0);
      }
    } catch (error) {
      console.error('중고거래 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    queueMicrotask(() => fetchProducts());
  }, [fetchProducts]);

  return (
    <Container>
      <UsedProductTable
        products={products}
        loading={loading}
        totalElements={totalElements}
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

export default UsedProductManagementPage;
