// src/components/owner/ai/AiStatGrid.jsx
import React from 'react';
import styled from 'styled-components';

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(${(props) => props.$columns}, 1fr);
  gap: 12px;
`;

const StatBox = styled.div`
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;

  .label {
    font-size: 12px;
    color: #8e94a0;
    margin-bottom: 6px;
  }
  .value {
    font-size: 20px;
    font-weight: bold;
    color: ${(props) => (props.$empty ? '#c2c7d0' : '#1a1f2c')};
  }
  .unit {
    font-size: 13px;
    font-weight: 600;
    color: #8e94a0;
    margin-left: 3px;
  }
`;

// 백엔드가 데이터 부족 시 null을 내려주므로 값 유무를 여기서 일괄 처리
function AiStatGrid({ stats, columns = 3 }) {
  return (
    <Grid $columns={columns}>
      {stats.map((stat) => {
        const isEmpty = stat.value === null || stat.value === undefined;

        return (
          <StatBox
            key={stat.label}
            $empty={isEmpty}
          >
            <div className="label">{stat.label}</div>
            <div className="value">
              {isEmpty ? '-' : stat.value}
              {!isEmpty && stat.unit && <span className="unit">{stat.unit}</span>}
            </div>
          </StatBox>
        );
      })}
    </Grid>
  );
}

export default AiStatGrid;
