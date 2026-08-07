import React from 'react';
import styled from 'styled-components';
import { Flag } from 'lucide-react';

const Banner = styled.div`
  background-color: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 24px;
`;

export default function NoticeBanner() {
  return (
    <Banner>
      <Flag size={18} color="#2563eb" style={{ marginTop: '2px' }} />
      <div style={{ fontSize: '13px' }}>
        <div
          style={{ fontWeight: 'bold', color: '#1d4ed8', marginBottom: '2px' }}
        >
          신고 내역 안내
        </div>
        <div style={{ color: '#3b82f6' }}>
          신고된 내용은 이음 운영팀에서 검토합니다. 처리 결과는 영업일 기준
          3~5일 내 안내됩니다. 악의적이거나 허위 신고는 제재될 수 있습니다.
        </div>
      </div>
    </Banner>
  );
}
