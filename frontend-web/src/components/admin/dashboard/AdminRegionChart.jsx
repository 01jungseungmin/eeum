import React from 'react';
import styled from 'styled-components';
import { ChevronRight } from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  min-width: 0;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }
  .more {
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #52c41a;
    cursor: pointer;
    font-weight: 600;
  }
`;

const ChartContainer = styled.div`
  width: 100%;
  height: 200px;
  position: relative;
`;

const chartData = [
  { name: '강남구', value: 2842 },
  { name: '서초구', value: 2104 },
  { name: '송파구', value: 1758 },
  { name: '마포구', value: 1342 },
  { name: '용산구', value: 982 },
  { name: '성동구', value: 765 },
].reverse();

function AdminRegionChart() {
  return (
    <Card>
      <Header>
        <h3>지역별 활동 사용자</h3>
        <span className="more">
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>

      <ChartContainer>
        <ResponsiveContainer width="100%" height="100%" aspect={2}>
          <BarChart
            layout="vertical"
            data={chartData}
            margin={{ top: 0, right: 10, left: 10, bottom: 0 }}
          >
            <XAxis type="number" hide />
            <YAxis
              dataKey="name"
              type="category"
              axisLine={false}
              tickLine={false}
              stroke="#262626"
              fontSize={12}
              fontWeight={600}
              width={50}
            />
            <Tooltip />
            <Bar
              dataKey="value"
              fill="#2d5a43"
              radius={[0, 4, 4, 0]}
              barSize={12}
            />
          </BarChart>
        </ResponsiveContainer>
      </ChartContainer>
    </Card>
  );
}

export default AdminRegionChart;
