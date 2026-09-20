import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import FavoriteStatFilterBar from '../../../components/admin/favorite/FavoriteStatFilterBar';
import FavoriteStatTable from '../../../components/admin/favorite/FavoriteStatTable';
import FavoriteRecalculatePanel from '../../../components/admin/favorite/FavoriteRecalculatePanel';
import { favoriteApi } from '../../../api/admin/favoriteApi';

const Container = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const PageTitle = styled.h1`
  font-size: 20px;
  font-weight: 700;
  color: #262626;
  margin: 0;
`;

// <input type="date"> 값(YYYY-MM-DD)을 하루의 시작/끝 LocalDateTime 문자열로 변환
const toStartOfDay = (dateStr) => (dateStr ? `${dateStr}T00:00:00` : undefined);
const toEndOfDay = (dateStr) => (dateStr ? `${dateStr}T23:59:59` : undefined);

function FavoriteStatsPage() {
  const [refType, setRefType] = useState('STORE');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [limit, setLimit] = useState(10);
  const [stats, setStats] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchStats = useCallback(async () => {
    setLoading(true);
    try {
      const params = { refType, limit };
      const fromParam = toStartOfDay(from);
      const toParam = toEndOfDay(to);
      if (fromParam) params.from = fromParam;
      if (toParam) params.to = toParam;

      const res = await favoriteApi.getStats(params);
      if (res.data?.success) {
        setStats(res.data.data || []);
      }
    } catch (error) {
      console.error('찜 통계 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [refType, from, to, limit]);

  useEffect(() => {
    queueMicrotask(() => fetchStats());
  }, [fetchStats]);

  return (
    <Container>
      <PageTitle>찜 통계</PageTitle>

      <FavoriteRecalculatePanel />

      <div>
        <FavoriteStatFilterBar
          refType={refType}
          onRefTypeChange={setRefType}
          from={from}
          onFromChange={setFrom}
          to={to}
          onToChange={setTo}
          limit={limit}
          onLimitChange={setLimit}
        />
        <FavoriteStatTable
          stats={stats}
          loading={loading}
        />
      </div>
    </Container>
  );
}

export default FavoriteStatsPage;
