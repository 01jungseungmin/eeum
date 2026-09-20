import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { Wallet, CheckCircle2, Clock, XCircle } from 'lucide-react';
import SettlementTable from '../../../components/admin/settlement/SettlementTable';
import PayoutProcessModal from '../../../components/admin/settlement/PayoutProcessModal';
import LateRevenueRecoveryCard from '../../../components/admin/settlement/LateRevenueRecoveryCard';
import { settlementApi } from '../../../api/admin/settlementApi';
import { WEEKLY_SETTLEMENT_CLAIMABLE_STATUSES } from '../../../constants/settlementConstants';
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
      font-size: 22px;
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

function SettlementManagementPage() {
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [processingSettlement, setProcessingSettlement] = useState(null);
  // 상단 카드로 고르는 보기 필터 (현재 불러온 페이지 안에서만 적용): ALL | COMPLETED | PENDING | FAILED
  const [cardFilter, setCardFilter] = useState('ALL');

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
        setTotalElements(res.data.data.totalElements || 0);
      }
    } catch (error) {
      console.error('정산 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page]);

  // 상태별/금액별 집계 API가 따로 없어 "이 페이지" 범위로만 계산한다.
  const pageStats = useMemo(() => {
    const completed = settlements.filter((s) => s.status === 'COMPLETED');
    const completedAmount = completed.reduce(
      (sum, s) => sum + Number(s.payoutAmount || 0),
      0,
    );
    const pendingCount = settlements.filter((s) =>
      WEEKLY_SETTLEMENT_CLAIMABLE_STATUSES.includes(s.status),
    ).length;
    const failedCount = settlements.filter(
      (s) => s.status === 'FAILED',
    ).length;

    return { completedAmount, pendingCount, failedCount };
  }, [settlements]);

  useEffect(() => {
    queueMicrotask(() => fetchSettlements());
  }, [fetchSettlements]);

  // 카드 숫자와 같은 기준으로 거른다
  const displayedSettlements = useMemo(() => {
    if (cardFilter === 'COMPLETED') {
      return settlements.filter((s) => s.status === 'COMPLETED');
    }
    if (cardFilter === 'PENDING') {
      return settlements.filter((s) =>
        WEEKLY_SETTLEMENT_CLAIMABLE_STATUSES.includes(s.status),
      );
    }
    if (cardFilter === 'FAILED') {
      return settlements.filter((s) => s.status === 'FAILED');
    }
    return settlements;
  }, [settlements, cardFilter]);

  const handleModalCompleted = () => {
    setProcessingSettlement(null);
    fetchSettlements();
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
            <span>전체 정산 건수</span>
            <h2>{totalElements.toLocaleString()}</h2>
            <p>전체 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <Wallet size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#edf5f1"
          $iconColor="#2d5a43"
          $clickable
          $active={cardFilter === 'COMPLETED'}
          onClick={() => setCardFilter('COMPLETED')}
        >
          <div className="info">
            <span>지급 완료 금액</span>
            <h2>{Math.round(pageStats.completedAmount).toLocaleString()}원</h2>
            <p>이 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <CheckCircle2 size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#ad6800"
          $clickable
          $active={cardFilter === 'PENDING'}
          onClick={() => setCardFilter('PENDING')}
        >
          <div className="info">
            <span>지급 대기</span>
            <h2>{pageStats.pendingCount}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <Clock size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fff1f0"
          $iconColor="#f5222d"
          $clickable
          $active={cardFilter === 'FAILED'}
          onClick={() => setCardFilter('FAILED')}
        >
          <div className="info">
            <span>지급 실패</span>
            <h2>{pageStats.failedCount}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <XCircle size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <LateRevenueRecoveryCard />

      <SettlementTable
        settlements={displayedSettlements}
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
