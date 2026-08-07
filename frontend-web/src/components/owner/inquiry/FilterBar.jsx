import React from 'react';
import styled from 'styled-components';
import { Search } from 'lucide-react';

const FilterBarContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fff;
  border: 1px solid #f1f3f5;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
`;

const SearchInputWrapper = styled.div`
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;

  .search-icon {
    position: absolute;
    left: 18px;
    color: #868e96;
  }
`;

const SearchInput = styled.input`
  width: 100%;
  padding: 14px 16px 14px 48px;
  border: 1px solid #f1f3f5;
  border-radius: 12px;
  font-size: 15px;
  background-color: #f1f3f5;
  outline: none;
  transition: all 0.2s;

  &:focus {
    background-color: #fff;
    border-color: #ced4da;
  }

  &::placeholder {
    color: #adb5bd;
  }
`;

const FilterGroupRow = styled.div`
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
`;

const LeftButtonGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const RightButtonGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
`;

const StatusButton = styled.button`
  padding: 8px 18px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease-in-out;

  border: 1px solid ${(props) => (props.$active ? '#111' : '#dee2e6')};
  background: ${(props) => (props.$active ? '#111' : '#fff')};
  color: ${(props) => (props.$active ? '#fff' : '#495057')};

  &:hover {
    background: ${(props) => (props.$active ? '#111' : '#f8f9fa')};
  }
`;

const TypeButton = styled.button`
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.15s ease-in-out;

  background: ${(props) => (props.$active ? '#111' : '#f1f3f5')};
  color: ${(props) => (props.$active ? '#fff' : '#495057')};

  &:hover {
    background: ${(props) => (props.$active ? '#111' : '#e9ecef')};
  }
`;

export default function FilterBar({
  searchTerm,
  setSearchTerm,
  statusFilter,
  setStatusFilter,
  typeFilter,
  setTypeFilter,
  counts = { total: 0, pending: 0, completed: 0 },
}) {
  const typeCategories = [
    '전체 유형',
    '상품 문의',
    '주문 문의',
    '예약 문의',
    '결제 문의',
    '기타',
  ];

  return (
    <FilterBarContainer>
      <SearchInputWrapper>
        <Search className="search-icon" size={18} />
        <SearchInput
          type="text"
          placeholder="고객명, 제목, 내용 검색..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </SearchInputWrapper>

      <FilterGroupRow>
        <LeftButtonGroup>
          <StatusButton
            $active={statusFilter === '전체'}
            onClick={() => setStatusFilter('전체')}
          >
            전체 ({counts.total})
          </StatusButton>
          <StatusButton
            $active={statusFilter === '미답변'}
            onClick={() => setStatusFilter('미답변')}
          >
            미답변 ({counts.pending})
          </StatusButton>
          <StatusButton
            $active={statusFilter === '답변완료'}
            onClick={() => setStatusFilter('답변완료')}
          >
            답변완료 ({counts.completed})
          </StatusButton>
        </LeftButtonGroup>

        <RightButtonGroup>
          {typeCategories.map((category) => (
            <TypeButton
              key={category}
              $active={typeFilter === category}
              onClick={() => setTypeFilter(category)}
            >
              {category}
            </TypeButton>
          ))}
        </RightButtonGroup>
      </FilterGroupRow>
    </FilterBarContainer>
  );
}
