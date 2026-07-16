import React from 'react';
import styled from 'styled-components';
import { Search } from 'lucide-react';

const ControlBarLayout = styled.div`
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  flex-direction: column;
  gap: 16px;
  @media (min-width: 768px) {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
`;
const SearchWrapper = styled.div`
  position: relative;
  flex: 1;
  max-width: 440px;
`;
const SearchIcon = styled(Search)`
  position: absolute;
  left: 12px;
  top: 10px;
  color: #94a3b8;
`;
const SearchInput = styled.input`
  width: 100%;
  padding: 10px 12px 10px 40px;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
  &:focus {
    outline: none;
    border-color: #10b981;
  }
`;
const FilterGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
`;
const FilterButton = styled.button`
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 12px;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
  background-color: ${(props) => (props.$active ? '#10b981' : '#f1f5f9')};
  color: ${(props) => (props.$active ? '#ffffff' : '#64748b')};
  font-weight: ${(props) => (props.$active ? '600' : '500')};
  &:hover {
    background-color: ${(props) => (props.$active ? '#059669' : '#e2e8f0')};
  }
`;

export default function ControlBar({
  searchTerm,
  setSearchTerm,
  activeFilter,
  setActiveFilter,
  totalCount,
}) {
  const tabs = [
    { id: 'ALL', label: `전체 (${totalCount})` },
    { id: 'VIP', label: '단골' },
    { id: 'NEW', label: '신규' },
    { id: 'FAV_ALERT', label: '즐겨찾기+알림' },
    { id: 'FAV', label: '즐겨찾기' },
    { id: 'ALERT', label: '알림 신청' },
  ];

  return (
    <ControlBarLayout>
      <SearchWrapper>
        <SearchIcon size={18} />
        <SearchInput
          type="text"
          placeholder="고객명, 연락처 검색..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </SearchWrapper>
      <FilterGroup>
        {tabs.map((tab) => (
          <FilterButton
            key={tab.id}
            $active={activeFilter === tab.id}
            onClick={() => setActiveFilter(tab.id)}
          >
            {tab.label}
          </FilterButton>
        ))}
      </FilterGroup>
    </ControlBarLayout>
  );
}
