import React, { useState } from 'react';
import styled from 'styled-components';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  ResponsiveContainer,
  CartesianGrid,
  Tooltip,
} from 'recharts';

const ChartCard = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  min-width: 0;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .title-side {
    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 4px 0 0 0;
      font-size: 12px;
      color: #8c8c8c;
    }
  }
`;

const TabContainer = styled.div`
  display: flex;
  gap: 4px;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 8px;
`;

const TabButton = styled.button`
  border: none;
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 6px;
  cursor: pointer;
  background: ${(props) => (props.$active ? '#2d5a43' : 'transparent')};
  color: ${(props) => (props.$active ? 'white' : '#595959')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
`;

const ChartContainer = styled.div`
  width: 100%;
  height: 200px;
  position: relative;
  min-width: 0; /* CSS Grid 크기 계산 오류 방지 */
`;

const mockData = [
  { name: '월', value: 220 },
  { name: '화', value: 310 },
  { name: '수', value: 270 },
  { name: '목', value: 410 },
  { name: '금', value: 460 },
  { name: '토', value: 530 },
  { name: '일', value: 500 },
];

function AdminUserChart() {
  const [activeTab, setActiveTab] = useState('일반');

  return (
    <ChartCard>
      <CardHeader>
        <div className="title-side">
          <h3>주간 가입자 추이</h3>
          <p>최근 7일 신규 가입 트렌드</p>
        </div>
        <TabContainer>
          <TabButton
            $active={activeTab === '일반'}
            onClick={() => setActiveTab('일반')}
          >
            일반
          </TabButton>
          <TabButton
            $active={activeTab === '사장'}
            onClick={() => setActiveTab('사장')}
          >
            사장
          </TabButton>
        </TabContainer>
      </CardHeader>

      <ChartContainer>
        {/* 2. minWidth={0} 추가 및 height를 숫자로 직접 지정 */}
        <ResponsiveContainer
          width="100%"
          height={200}
          minWidth={0}
        >
          <BarChart
            data={mockData}
            margin={{ top: 10, right: 10, left: -25, bottom: 0 }}
          >
            <CartesianGrid
              strokeDasharray="3 3"
              vertical={false}
              stroke="#f5f5f5"
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
              axisLine={false}
              tickLine={false}
            />
            <Tooltip />
            <Bar
              dataKey="value"
              fill="#2d5a43"
              radius={[4, 4, 0, 0]}
              barSize={26}
            />
          </BarChart>
        </ResponsiveContainer>
      </ChartContainer>

      <div
        style={{
          textAlign: 'center',
          fontSize: '12px',
          color: '#bfbfbf',
          marginTop: '12px',
        }}
      >
        52+8
      </div>
    </ChartCard>
  );
}

export default AdminUserChart;
