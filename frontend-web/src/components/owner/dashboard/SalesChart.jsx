import { useEffect, useState } from 'react';
import styled from 'styled-components';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ResponsiveContainer,
} from 'recharts';
import { settlementApi } from '../../../api/owner/settlementApi';

const ChartCard = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;

  .title-group {
    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 4px 0 0;
      font-size: 12px;
      color: #8c8c8c;
    }
  }
  .total-badge {
    background-color: #e6f7ff;
    color: #1d392a;
    font-weight: bold;
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 13px;
    background: #edf5f1;
  }
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

// 값이 작을 때(만원 미만 구간) 반올림하면 눈금이 겹쳐 보이므로 소수 첫째 자리까지 표기
const formatManwonTick = (value) => {
  const manwon = value / 10000;
  return `${manwon.toFixed(value > 0 && value < 100000 ? 1 : 0)}만`;
};

// 최근 7일(오늘 포함) 일별 버킷
const buildLast7DaysBuckets = () => {
  const buckets = [];
  const now = new Date();

  for (let i = 6; i >= 0; i -= 1) {
    const start = new Date(now);
    start.setHours(0, 0, 0, 0);
    start.setDate(start.getDate() - i);
    const end = new Date(start);
    end.setDate(end.getDate() + 1);
    buckets.push({ label: `${start.getMonth() + 1}/${start.getDate()}`, start, end });
  }
  return buckets;
};

function SalesChart() {
  const [revenues, setRevenues] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      try {
        // 최근 매출 위주 최신순 정렬이라, 최근 7일 집계에는 한 페이지면 충분하다.
        const res = await settlementApi.getRevenues({ page: 0, size: 100 });
        if (res.data?.success) {
          setRevenues(res.data.data.content || []);
        }
      } catch (error) {
        console.error('매출 차트 데이터 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };
    queueMicrotask(() => load());
  }, []);

  const buckets = buildLast7DaysBuckets();
  const countable = revenues.filter((r) => r.status !== 'CANCELLED');

  const chartData = buckets.map((bucket) => {
    const total = countable
      .filter((r) => {
        const createdAt = new Date(r.createdAt);
        return createdAt >= bucket.start && createdAt < bucket.end;
      })
      .reduce((sum, r) => sum + Number(r.paymentAmount || 0), 0);
    return { name: bucket.label, revenue: total };
  });

  const weekTotal = chartData.reduce((sum, d) => sum + d.revenue, 0);

  return (
    <ChartCard>
      <CardHeader>
        <div className="title-group">
          <h3>주간 매출 현황</h3>
          <p>최근 7일 매출 추이</p>
        </div>
        <div className="total-badge">
          총 {Math.round(weekTotal / 10000).toLocaleString()}만원
        </div>
      </CardHeader>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : weekTotal === 0 ? (
        <EmptyText>최근 7일간 매출이 없어요.</EmptyText>
      ) : (
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
                id="colorRevenue"
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
            <Tooltip
              formatter={(value) => `${Math.round(value).toLocaleString()}원`}
            />
            <Area
              type="monotone"
              dataKey="revenue"
              stroke="#2d5a43"
              strokeWidth={2}
              fillOpacity={1}
              fill="url(#colorRevenue)"
            />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

export default SalesChart;
