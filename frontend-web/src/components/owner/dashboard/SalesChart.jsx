import React from 'react';
import styled from 'styled-components';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';

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

const data = [
  { name: '4/22', revenue: 14 },
  { name: '4/23', revenue: 22 },
  { name: '4/24', revenue: 20 },
  { name: '4/25', revenue: 31 },
  { name: '4/26', revenue: 28 },
  { name: '4/27', revenue: 19 },
  { name: '4/28', revenue: 35 },
];

function SalesChart() {
  return (
    <ChartCard>
      <CardHeader>
        <div className="title-group">
          <h3>주간 매출 현황</h3>
          <p>최근 7일 매출 추이</p>
        </div>
        <div className="total-badge">총 168만원</div>
      </CardHeader>

      <div style={{ width: '100%', height: 220 }}>
        <ResponsiveContainer>
          <AreaChart
            data={data}
            margin={{ top: 10, right: 10, left: -20, bottom: 0 }}
          >
            <defs>
              <linearGradient id="colorRevenue" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#2d5a43" stopOpacity={0.2} />
                <stop offset="95%" stopColor="#2d5a43" stopOpacity={0} />
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
              tickFormatter={(v) => `${v}만`}
            />
            <Tooltip />
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
      </div>
    </ChartCard>
  );
}

export default SalesChart;
