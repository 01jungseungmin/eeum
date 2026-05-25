import React from 'react';
import styled from 'styled-components';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  height: 100%;
`;

const Title = styled.h3`
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #262626;
`;
const SubTitle = styled.p`
  margin: 4px 0 15px 0;
  font-size: 12px;
  color: #8c8c8c;
`;

const LegendContainer = styled.div`
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const LegendItem = styled.div`
  display: flex;
  justify-content: space-between;
  font-size: 13px;

  .label-side {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #595959;
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: ${(props) => props.$color};
  }
  .value-side {
    font-weight: 700;
    color: #262626;
  }
`;

const data = [
  { name: '반찬류', value: 55, color: '#2d5a43' },
  { name: '국/찌개', value: 25, color: '#143022' },
  { name: '나물류', value: 12, color: '#7cb342' },
  { name: '기타', value: 8, color: '#c5e1a5' },
];

function CategoryChart() {
  return (
    <Card>
      <Title>카테고리별 판매</Title>
      <SubTitle>이번 달 기준</SubTitle>

      <div style={{ width: '100%', height: 140 }}>
        <ResponsiveContainer>
          <PieChart>
            <Pie
              data={data}
              innerRadius={45}
              outerRadius={60}
              paddingAngle={3}
              dataKey="value"
            >
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
      </div>

      <LegendContainer>
        {data.map((item) => (
          <LegendItem key={item.name} $color={item.color}>
            <div className="label-side">
              <span className="dot" />
              <span>{item.name}</span>
            </div>
            <div className="value-side">{item.value}%</div>
          </LegendItem>
        ))}
      </LegendContainer>
    </Card>
  );
}

export default CategoryChart;
