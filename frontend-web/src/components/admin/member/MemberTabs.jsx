import React from 'react';
import styled from 'styled-components';

const TabContainer = styled.div`
  background: white;
  border-left: 1px solid #e8e8e8;
  border-right: 1px solid #e8e8e8;
  display: flex;
  gap: 24px;
  padding: 0 24px;
  border-bottom: 1px solid #f0f0f0;
`;

const TabItem = styled.div`
  padding: 16px 4px;
  font-size: 14px;
  font-weight: ${(props) => (props.$active ? '700' : '500')};
  color: ${(props) => (props.$active ? '#2d5a43' : '#666')};
  border-bottom: 2px solid
    ${(props) => (props.$active ? '#2d5a43' : 'transparent')};
  cursor: pointer;
  transition: all 0.2s;

  span {
    font-size: 12px;
    margin-left: 4px;
    opacity: 0.8;
    color: ${(props) => (props.$active ? '#2d5a43' : '#999')};
    font-weight: 500;
  }
`;

function MemberTabs({ activeTab, setActiveTab, rawData }) {
  const totalCount = rawData.length;
  const generalCount = rawData.filter((m) => m.role === 'ROLE_USER').length;
  const ownerCount = rawData.filter((m) => m.role === 'ROLE_OWNER').length;
  const suspendedCount = rawData.filter((m) => m.status === 'SUSPENDED').length;

  const tabs = [
    { id: 'all', label: '전체', count: `${totalCount}명` },
    { id: 'general', label: '일반 회원', count: generalCount },
    { id: 'owner', label: '사장 회원', count: ownerCount },
    { id: 'suspended', label: '정지 회원', count: suspendedCount },
  ];

  return (
    <TabContainer>
      {tabs.map((tab) => (
        <TabItem
          key={tab.id}
          $active={activeTab === tab.id}
          onClick={() => setActiveTab(tab.id)}
        >
          {tab.label}
          <span>{tab.count}</span>
        </TabItem>
      ))}
    </TabContainer>
  );
}

export default MemberTabs;
