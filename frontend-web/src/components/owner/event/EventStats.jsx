// src/components/owner/event/EventStats.jsx
import React from 'react';
import styled from 'styled-components';
import { Zap, Clock, Tag } from 'lucide-react';

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
`;

const StatCard = styled.div`
  background: white;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.01);
  border: 1px solid #eef0f2;

  &.active {
    background: #1c5335;
    color: white;
    * {
      color: white;
    }
  }

  .icon-box {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    background: #f1f3f5;
    display: flex;
    justify-content: center;
    align-items: center;
    color: #4a5568;

    &.active-icon {
      background: rgba(255, 255, 255, 0.15);
      color: #ffca28;
    }
  }

  .info {
    .label {
      font-size: 12px;
      color: #8e94a0;
      margin-bottom: 4px;
    }
    .count {
      font-size: 24px;
      font-weight: bold;
      color: #1a1f2c;
      span {
        font-size: 14px;
        font-weight: normal;
      }
    }
  }
`;

function EventStats({ liveCount, readyCount, totalCount }) {
  return (
    <StatsGrid>
      <StatCard className="active">
        <div className="icon-box active-icon">
          <Zap size={20} strokeWidth={2.5} />
        </div>
        <div className="info">
          <div className="label" style={{ color: 'rgba(255,255,255,0.7)' }}>
            진행중 이벤트
          </div>
          <div className="count">{liveCount}</div>
        </div>
      </StatCard>

      <StatCard>
        <div className="icon-box">
          <Clock size={20} color="#1a73e8" />
        </div>
        <div className="info">
          <div className="label">예정 이벤트</div>
          <div className="count">{readyCount}</div>
        </div>
      </StatCard>

      <StatCard>
        <div className="icon-box">
          <Tag size={20} color="#00a651" />
        </div>
        <div className="info">
          <div className="label">전체 이벤트</div>
          <div className="count">{totalCount}</div>
        </div>
      </StatCard>
    </StatsGrid>
  );
}

export default EventStats;
