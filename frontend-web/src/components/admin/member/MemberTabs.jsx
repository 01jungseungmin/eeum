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
    font-size: 14px;
    margin-left: 6px;
    color: ${(props) => (props.$active ? '#2d5a43' : '#8c8c8c')};
    font-weight: ${(props) => (props.$active ? '700' : '500')};
  }
`;

function MemberTabs({
  activeTab,
  setActiveTab,
  tabCounts = { all: 0, general: 0, owner: 0, suspended: 0 },
}) {
  const formatNumber = (num) => {
    if (num === undefined || num === null) return '0';
    return num.toLocaleString();
  };

  const tabs = [
    { id: 'all', label: '전체', count: tabCounts?.all ?? 0 },
    { id: 'general', label: '일반 회원', count: tabCounts?.general ?? 0 },
    { id: 'owner', label: '사장 회원', count: tabCounts?.owner ?? 0 },
    { id: 'suspended', label: '정지 회원', count: tabCounts?.suspended ?? 0 },
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
          <span>{formatNumber(tab.count)}</span>
        </TabItem>
      ))}
    </TabContainer>
  );
}

export default MemberTabs;
