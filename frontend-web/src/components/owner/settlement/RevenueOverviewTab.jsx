import { useMemo, useState } from 'react';
import styled from 'styled-components';
import { Download } from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ResponsiveContainer,
} from 'recharts';
import { OWNER_REVENUE_STATUS_LABEL } from '../../../constants/settlementConstants';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
`;

const ToolbarRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
`;

const ModeToggle = styled.div`
  display: flex;
  background: #f1f3f5;
  border-radius: 10px;
  padding: 4px;
  gap: 4px;
`;

const ModeButton = styled.button`
  border: none;
  padding: 8px 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  background: ${(props) => (props.$active ? '#2d5a43' : 'transparent')};
  color: ${(props) => (props.$active ? 'white' : '#595959')};
`;

const ExportButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  background: white;
  color: #595959;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background: #f8f9fa;
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const StatGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
`;

const StatBox = styled.div`
  background: #fafafa;
  border-radius: 12px;
  padding: 18px 20px;

  .label {
    font-size: 13px;
    color: #8c8c8c;
    margin-bottom: 8px;
  }
  .value {
    font-size: 22px;
    font-weight: 700;
    color: #262626;
  }
`;

const TableTitle = styled.h4`
  margin: 28px 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: #262626;
`;

const TableWrapper = styled.div`
  overflow-x: auto;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    text-align: left;
    padding: 10px 12px;
    color: #8c8c8c;
    font-weight: 600;
    border-bottom: 1px solid #f0f0f0;
    white-space: nowrap;
  }
  td {
    padding: 12px;
    border-bottom: 1px solid #f5f5f5;
    color: #262626;
    white-space: nowrap;
  }
  tr:last-child td {
    border-bottom: none;
  }
`;

const Fee = styled.span`
  color: #ff4d4f;
`;

const StatusPill = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => (props.$muted ? '#f5f5f5' : '#edf5f1')};
  color: ${(props) => (props.$muted ? '#8c8c8c' : '#2d5a43')};
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const won = (value) => `${Math.round(Number(value || 0)).toLocaleString()}원`;

// 값이 작을 때(예: 5,000~15,000원대) 만원 단위로 반올림하면 "0만"/"1만"이 중복 표시되므로,
// 10만원 미만 구간에서는 소수 첫째 자리까지 보여 눈금이 서로 구분되게 한다.
const formatManwonTick = (value) => {
  const manwon = value / 10000;
  return `${manwon.toFixed(value > 0 && value < 100000 ? 1 : 0)}만`;
};

// 최근 7일(일별) / 최근 6개월(월별) 버킷 목록을 만든다.
const buildBuckets = (mode) => {
  const buckets = [];
  const now = new Date();

  if (mode === 'monthly') {
    for (let i = 5; i >= 0; i -= 1) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      buckets.push({
        key: `${d.getFullYear()}-${d.getMonth()}`,
        label: `${d.getMonth() + 1}월`,
        start: d,
        end: new Date(d.getFullYear(), d.getMonth() + 1, 1),
      });
    }
    return buckets;
  }

  for (let i = 6; i >= 0; i -= 1) {
    const d = new Date(now);
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() - i);
    const end = new Date(d);
    end.setDate(end.getDate() + 1);
    buckets.push({
      key: d.toISOString().slice(0, 10),
      label: `${d.getMonth() + 1}/${d.getDate()}`,
      start: d,
      end,
    });
  }
  return buckets;
};

function RevenueOverviewTab({ revenues, loading }) {
  const [mode, setMode] = useState('daily');

  const buckets = useMemo(() => buildBuckets(mode), [mode]);
  const rangeStart = buckets[0]?.start;
  const rangeEnd = buckets[buckets.length - 1]?.end;

  const revenuesInRange = useMemo(
    () =>
      revenues.filter((r) => {
        const createdAt = new Date(r.createdAt);
        return createdAt >= rangeStart && createdAt < rangeEnd;
      }),
    [revenues, rangeStart, rangeEnd],
  );

  const countableInRange = useMemo(
    () => revenuesInRange.filter((r) => r.status !== 'CANCELLED'),
    [revenuesInRange],
  );

  const chartData = useMemo(
    () =>
      buckets.map((bucket) => {
        const total = revenues
          .filter((r) => {
            if (r.status === 'CANCELLED') return false;
            const createdAt = new Date(r.createdAt);
            return createdAt >= bucket.start && createdAt < bucket.end;
          })
          .reduce((sum, r) => sum + Number(r.paymentAmount || 0), 0);
        return { name: bucket.label, revenue: total };
      }),
    [buckets, revenues],
  );

  const totalRevenue = countableInRange.reduce(
    (sum, r) => sum + Number(r.paymentAmount || 0),
    0,
  );
  const orderCount = countableInRange.length;
  const averageOrderAmount = orderCount > 0 ? totalRevenue / orderCount : 0;

  const tableRows = [...revenuesInRange].sort(
    (a, b) => new Date(b.createdAt) - new Date(a.createdAt),
  );

  const handleExport = () => {
    const header = ['정산ID', '발생일', '결제금액', '수수료', '정산금액', '상태'];
    const rows = tableRows.map((r) => [
      r.ownerRevenueId,
      new Date(r.createdAt).toLocaleString('ko-KR'),
      r.paymentAmount,
      Number(r.pgFeeAmount || 0) + Number(r.platformFeeAmount || 0),
      r.payoutAmount,
      OWNER_REVENUE_STATUS_LABEL[r.status] || r.status,
    ]);

    const csv = [header, ...rows]
      .map((line) => line.map((cell) => `"${cell}"`).join(','))
      .join('\n');

    const bom = String.fromCharCode(0xfeff);
    const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `매출내역_${mode === 'daily' ? '일별' : '월별'}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <Card>
      <ToolbarRow>
        <ModeToggle>
          <ModeButton
            $active={mode === 'daily'}
            onClick={() => setMode('daily')}
          >
            일별
          </ModeButton>
          <ModeButton
            $active={mode === 'monthly'}
            onClick={() => setMode('monthly')}
          >
            월별
          </ModeButton>
        </ModeToggle>

        <ExportButton
          onClick={handleExport}
          disabled={tableRows.length === 0}
        >
          <Download size={14} />
          내보내기
        </ExportButton>
      </ToolbarRow>

      <StatGrid>
        <StatBox>
          <div className="label">총 매출</div>
          <div className="value">{won(totalRevenue)}</div>
        </StatBox>
        <StatBox>
          <div className="label">총 주문수</div>
          <div className="value">{orderCount.toLocaleString()}건</div>
        </StatBox>
        <StatBox>
          <div className="label">평균 주문금액</div>
          <div className="value">{won(averageOrderAmount)}</div>
        </StatBox>
      </StatGrid>

      <ResponsiveContainer
        width="100%"
        height={220}
      >
        <AreaChart
          data={chartData}
          margin={{ top: 10, right: 10, left: -20, bottom: 0 }}
        >
          <defs>
            <linearGradient
              id="colorSettlementRevenue"
              x1="0"
              y1="0"
              x2="0"
              y2="1"
            >
              <stop
                offset="5%"
                stopColor="#2d5a43"
                stopOpacity={0.2}
              />
              <stop
                offset="95%"
                stopColor="#2d5a43"
                stopOpacity={0}
              />
            </linearGradient>
          </defs>
          <CartesianGrid
            strokeDasharray="3 3"
            vertical={false}
            stroke="#f0f0f0"
          />
          <XAxis
            dataKey="name"
            stroke="#bfbfbf"
            fontSize={11}
            tickLine={false}
          />
          <YAxis
            stroke="#bfbfbf"
            fontSize={11}
            tickLine={false}
            axisLine={false}
            tickFormatter={formatManwonTick}
          />
          <Tooltip formatter={(value) => won(value)} />
          <Area
            type="monotone"
            dataKey="revenue"
            stroke="#2d5a43"
            strokeWidth={2}
            fillOpacity={1}
            fill="url(#colorSettlementRevenue)"
          />
        </AreaChart>
      </ResponsiveContainer>

      <TableTitle>주문별 매출 내역</TableTitle>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : tableRows.length === 0 ? (
        <EmptyText>선택한 기간에 매출 내역이 없어요.</EmptyText>
      ) : (
        <TableWrapper>
          <Table>
            <thead>
              <tr>
                <th>정산 ID</th>
                <th>발생일</th>
                <th>결제금액</th>
                <th>수수료</th>
                <th>정산금액</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {tableRows.map((r) => (
                <tr key={r.ownerRevenueId}>
                  <td>#{r.ownerRevenueId}</td>
                  <td>
                    {new Date(r.createdAt).toLocaleDateString('ko-KR')}
                  </td>
                  <td>{won(r.paymentAmount)}</td>
                  <td>
                    <Fee>
                      -{won(Number(r.pgFeeAmount || 0) + Number(r.platformFeeAmount || 0))}
                    </Fee>
                  </td>
                  <td>
                    <strong>{won(r.payoutAmount)}</strong>
                  </td>
                  <td>
                    <StatusPill $muted={r.status === 'CANCELLED'}>
                      {OWNER_REVENUE_STATUS_LABEL[r.status] || r.status}
                    </StatusPill>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </TableWrapper>
      )}
    </Card>
  );
}

export default RevenueOverviewTab;
