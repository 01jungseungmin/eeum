import React from 'react';
import styled from 'styled-components';
import { ChevronRight } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis } from 'recharts';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
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

const data = [
  { name: '김치찌개 반찬', value: 55 },
  { name: '된장찌개 반찬', value: 40 },
  { name: '불고기 반찬', value: 33 },
  { name: '잡채', value: 20 },
  { name: '계란말이', value: 12 },
].reverse();

function TopProducts() {
  return (
    <Card>
      <Header>
        <h3>인기 상품 TOP 5</h3>
        <span className="more">
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>
      <div style={{ width: '100%', height: 220 }}>
        <BarChart
          width={300}
          height={220}
          layout="vertical"
          data={data}
          margin={{ top: 0, right: 20, left: 30, bottom: 0 }}
          style={{ width: '100%' }}
        >
          <XAxis type="number" hide />
          <YAxis
            dataKey="name"
            type="category"
            axisLine={false}
            tickLine={false}
            stroke="#595959"
            fontSize={11}
          />
          <Bar
            dataKey="value"
            fill="#5fa07e"
            radius={[0, 6, 6, 0]}
            barSize={14}
          />
        </BarChart>
      </div>
    </Card>
  );
}

export default TopProducts;
