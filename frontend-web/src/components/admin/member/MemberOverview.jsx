import React from 'react';
import styled from 'styled-components';
import { Download } from 'lucide-react';

const OverviewContainer = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
`;
const TitleBlock = styled.div`
  h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
    color: #1a1a1a;
  }
  p {
    margin: 6px 0 0;
    font-size: 13px;
    color: #666;
  }
`;
const ButtonGroup = styled.div`
  display: flex;
  gap: 10px;
  button {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 16px;
    font-size: 13px;
    font-weight: 600;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
  }
  .btn-download {
    background: white;
    color: #333;
    border: 1px solid #d9d9d9;
    &:hover {
      background: #f5f5f5;
    }
  }
  .btn-csv {
    background: #1a392a;
    color: white;
    border: none;
    &:hover {
      background: #0f241a;
    }
  }
`;

function MemberOverview({ total, suspended }) {
  return (
    <OverviewContainer>
      <TitleBlock>
        <h1>회원 관리</h1>
        <p>
          전체 {total.toLocaleString()}명 · 정지 {suspended}명
        </p>
      </TitleBlock>
      <ButtonGroup>
        <button className="btn-download">
          <Download size={14} /> 회원 내보내기
        </button>
        <button className="btn-csv">
          <Download size={14} /> CSV 내보내기
        </button>
      </ButtonGroup>
    </OverviewContainer>
  );
}

export default MemberOverview;
