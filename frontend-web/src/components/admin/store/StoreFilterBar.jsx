import { useState } from 'react';
import styled from 'styled-components';
import { Search } from 'lucide-react';
import { STORE_STATUS_FILTER_OPTIONS } from '../../../constants/storeConstants';

const FilterRow = styled.div`
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
`;

const SearchBox = styled.form`
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 10px 14px;

  input {
    flex: 1;
    border: none;
    outline: none;
    font-size: 14px;
  }
`;

const StatusSelect = styled.select`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 14px;
  color: #262626;
  background: white;
  cursor: pointer;
`;

function StoreFilterBar({ keyword, onSearch, status, onStatusChange }) {
  const [keywordInput, setKeywordInput] = useState(keyword);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch(keywordInput.trim());
  };

  return (
    <FilterRow>
      <SearchBox onSubmit={handleSubmit}>
        <Search
          size={16}
          color="#9CA3AF"
        />
        <input
          type="text"
          placeholder="상점명, 사업자명으로 검색"
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
        />
      </SearchBox>

      <StatusSelect
        value={status}
        onChange={(e) => onStatusChange(e.target.value)}
      >
        {STORE_STATUS_FILTER_OPTIONS.map((opt) => (
          <option
            key={opt.value}
            value={opt.value}
          >
            {opt.label}
          </option>
        ))}
      </StatusSelect>
    </FilterRow>
  );
}

export default StoreFilterBar;
