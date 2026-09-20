import styled from 'styled-components';
import { Search } from 'lucide-react';
import { OPERATION_FAILURE_CATEGORY_FILTER_OPTIONS } from '../../../constants/operationConstants';

const FilterRow = styled.div`
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
`;

const CategorySelect = styled.select`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 14px;
  height: 40px;
  font-size: 14px;
  color: #262626;
  background: white;
  cursor: pointer;
`;

const SearchBox = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  background: white;
  flex: 1;
  min-width: 200px;

  input {
    flex: 1;
    border: none;
    outline: none;
    font-size: 14px;
  }
`;

function OperationFailureFilterBar({
  category,
  onCategoryChange,
  keyword,
  onKeywordChange,
}) {
  return (
    <FilterRow>
      <CategorySelect
        value={category}
        onChange={(e) => onCategoryChange(e.target.value)}
      >
        {OPERATION_FAILURE_CATEGORY_FILTER_OPTIONS.map((opt) => (
          <option
            key={opt.value}
            value={opt.value}
          >
            {opt.label}
          </option>
        ))}
      </CategorySelect>

      <SearchBox>
        <Search
          size={14}
          color="#9ca3af"
        />
        <input
          type="text"
          placeholder="작업명 · 에러코드 · 메시지 검색"
          value={keyword}
          onChange={(e) => onKeywordChange(e.target.value)}
        />
      </SearchBox>
    </FilterRow>
  );
}

export default OperationFailureFilterBar;
