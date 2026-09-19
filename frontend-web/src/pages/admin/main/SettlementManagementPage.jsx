import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import SettlementTable from '../../../components/admin/settlement/SettlementTable';
import PayoutProcessModal from '../../../components/admin/settlement/PayoutProcessModal';
import LateRevenueRecoveryCard from '../../../components/admin/settlement/LateRevenueRecoveryCard';
import { settlementApi } from '../../../api/admin/settlementApi';

const Container = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
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

function SettlementManagementPage() {
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [processingSettlement, setProcessingSettlement] = useState(null);

  const fetchSettlements = useCallback(async () => {
    setLoading(true);
    try {
      const res = await settlementApi.getWeeklySettlements({
        page,
        size: PAGE_SIZE,
      });
      if (res.data?.success) {
        setSettlements(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
      }
    } catch (error) {
      console.error('정산 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    queueMicrotask(() => fetchSettlements());
  }, [fetchSettlements]);

  const handleModalCompleted = () => {
    setProcessingSettlement(null);
    fetchSettlements();
  };

  return (
    <Container>
      <LateRevenueRecoveryCard />

      <SettlementTable
        settlements={settlements}
        loading={loading}
        onProcessClick={setProcessingSettlement}
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

      {processingSettlement && (
        <PayoutProcessModal
          settlement={processingSettlement}
          onClose={() => setProcessingSettlement(null)}
          onCompleted={handleModalCompleted}
        />
      )}
    </Container>
  );
}

export default SettlementManagementPage;
