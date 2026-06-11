import React, { useState } from 'react';
import styled from 'styled-components';
import { ChevronDown } from 'lucide-react';

const FilterWrapper = styled.div`
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 16px 16px 0 0;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SearchRow = styled.div`
  display: flex;
  gap: 12px;

  .search-input {
    flex: 1;
    padding: 10px 16px;
    border: 1px solid #e8e8e8;
    background: #f5f5f5;
    border-radius: 12px;
    font-size: 14px;
    outline: none;
    &:focus {
      border-color: #2d5a43;
      background: white;
    }
  }

  select {
    padding: 0 16px;
    border: 1px solid #e8e8e8;
    background: #f5f5f5;
    border-radius: 12px;
    font-size: 14px;
    cursor: pointer;
    outline: none;
  }

  .btn-filter {
    padding: 0 20px;
    background: white;
    border: 1px solid #d9d9d9;
    border-radius: 12px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    &:hover {
      background: #f5f5f5;
    }
  }
`;

const ActionRow = styled.div`
  background: #f0f5f2;
  border: 1px solid #bae7cc;
  border-radius: 10px;
  padding: 10px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .selected-count {
    font-size: 13px;
    color: #005936;
    font-weight: 600;
  }

  .action-buttons {
    display: flex;
    gap: 8px;
    button {
      padding: 6px 12px;
      font-size: 12px;
      font-weight: 600;
      background: white;
      border: 1px solid #d9d9d9;
      border-radius: 6px;
      cursor: pointer;
      &:hover {
        border-color: #ff4d4f;
        color: #ff4d4f;
      }
    }
  }
`;

function MemberFilterBar({
  selectedCount,
  onBulkSuspend,
  onBulkActivate,
  onApplyFilter,
}) {
  const [statusFilter, setStatusFilter] = useState('ALL');

  const handleFilterSubmit = () => {
    if (onApplyFilter) {
      onApplyFilter(statusFilter);
    }
  };

  return (
    <FilterWrapper>
      <SearchRow>
        <input
          className="search-input"
          type="text"
          placeholder="이름, 이메일, 전화번호로 검색"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="ALL">전체 상태</option>
          <option value="ACTIVE">활성</option>
          <option value="SUSPENDED">정지</option>
        </select>

        <button className="btn-filter" onClick={handleFilterSubmit}>
          필터 적용
        </button>
      </SearchRow>

      {selectedCount > 0 && (
        <ActionRow>
          <span className="selected-count">{selectedCount}명 선택됨</span>
          <div className="action-buttons">
            <button onClick={onBulkSuspend}>일결 정지</button>
            <button onClick={onBulkActivate}>일괄 해제</button>
          </div>
        </ActionRow>
      )}
    </FilterWrapper>
  );
}

export default MemberFilterBar;
