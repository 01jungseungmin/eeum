import React from 'react';
import styled from 'styled-components';

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
`;

const StatCard = styled.div`
  background: white;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  border: 1px solid #eef0f2;

  .label {
    font-size: 12px;
    color: #8e94a0;
    margin-bottom: 12px;
    font-weight: 500;
  }
  .value {
    font-size: 28px;
    font-weight: bold;
    color: #1a1f2c;
  }
`;

function CategoryStats({ totalCount, activeCount, totalProducts }) {
  return (
    <StatsGrid>
      <StatCard>
        <div className="label">전체 카테고리</div>
        <div className="value">{totalCount}</div>
      </StatCard>
      <StatCard>
        <div className="label">노출 중</div>
        <div className="value">{activeCount}</div>
      </StatCard>
      <StatCard>
        <div className="label">등록 상품</div>
        <div className="value">{totalProducts}</div>
      </StatCard>
    </StatsGrid>
  );
}

export default CategoryStats;
