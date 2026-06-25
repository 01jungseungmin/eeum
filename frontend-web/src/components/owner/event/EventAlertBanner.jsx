// src/components/owner/event/EventAlertBanner.jsx
import React from 'react';
import styled from 'styled-components';
import { AlertTriangle } from 'lucide-react';

const AlertBanner = styled.div`
  background: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 12px;
  padding: 14px 18px;
  margin-bottom: 24px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #b45309;
  font-size: 12px;
  line-height: 1.5;

  .title {
    font-weight: bold;
    color: #92400e;
    margin-bottom: 2px;
  }
`;

function EventAlertBanner() {
  return (
    <AlertBanner>
      <AlertTriangle size={16} style={{ marginTop: '2px', flexShrink: 0 }} />
      <div>
        <div className="title">이벤트 가격 등록 안내</div>
        <div>
          할인율 입력 시 이벤트 가격이 자동 계산되고, 이벤트 가격 입력 시
          할인율이 자동 계산됩니다. 둘 중 하나만 입력하세요.
        </div>
      </div>
    </AlertBanner>
  );
}

export default EventAlertBanner;
