import React from 'react';
import styled from 'styled-components';
import { Search } from 'lucide-react';

const FilterCard = styled.div`
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 24px;
`;

const SearchContainer = styled.div`
  position: relative;
  margin-bottom: 16px;

  input {
    width: 100%;
    padding: 12px 16px 12px 42px;
    border-radius: 12px;
    border: 1px solid #f1f5f9;
    background-color: #f8fafc;
    font-size: 13px;
    box-sizing: border-box;
    outline: none;

    &::placeholder {
      color: #94a3b8;
    }
  }

  svg {
    position: absolute;
    left: 14px;
    top: 50%;
    transform: translateY(-50%);
    color: #94a3b8;
  }
`;

const FilterRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 8px;
`;

const FilterBtn = styled.button`
  padding: 7px 16px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid ${(props) => (props.$active ? 'transparent' : '#e2e8f0')};
  background-color: ${(props) =>
    props.$active ? (props.$dark ? '#1a2e26' : '#34d399') : '#ffffff'};
  color: ${(props) => (props.$active ? '#ffffff' : '#64748b')};
  cursor: pointer;
  transition: all 0.15s ease-in-out;
`;

export default function FilterSection({
  selectedStatus,
  onStatusChange,
  selectedType,
  onTypeChange,
  searchKeyword,
  onSearchChange,
}) {
  return (
    <FilterCard>
      <SearchContainer>
        <Search size={18} />
        <input
          type="text"
          placeholder="신고 내용, 신고자 검색..."
          value={searchKeyword}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </SearchContainer>

      <FilterRow>
        {/* 상태 필터 (ALL, PENDING, REVIEWED, DISMISSED) */}
        <ButtonGroup>
          <FilterBtn
            $active={selectedStatus === 'ALL'}
            onClick={() => onStatusChange('ALL')}
          >
            전체
          </FilterBtn>
          <FilterBtn
            $active={selectedStatus === 'PENDING'}
            onClick={() => onStatusChange('PENDING')}
          >
            검토중
          </FilterBtn>
          <FilterBtn
            $active={selectedStatus === 'REVIEWED'}
            onClick={() => onStatusChange('REVIEWED')}
          >
            처리완료
          </FilterBtn>
          <FilterBtn
            $active={selectedStatus === 'DISMISSED'}
            onClick={() => onStatusChange('DISMISSED')}
          >
            기각
          </FilterBtn>
        </ButtonGroup>

        {/* 유형 필터 (ALL, STORE_REVIEW, COMMUNITY_POST, STORE 등) */}
        <ButtonGroup>
          <FilterBtn
            $active={selectedType === 'ALL'}
            $dark
            onClick={() => onTypeChange('ALL')}
          >
            전체 유형
          </FilterBtn>
          <FilterBtn
            $active={selectedType === 'STORE_REVIEW'}
            $dark
            onClick={() => onTypeChange('STORE_REVIEW')}
          >
            리뷰 신고
          </FilterBtn>
          <FilterBtn
            $active={selectedType === 'COMMUNITY_COMMENT'}
            $dark
            onClick={() => onTypeChange('COMMUNITY_COMMENT')}
          >
            채팅 신고
          </FilterBtn>
          <FilterBtn
            $active={selectedType === 'STORE'}
            $dark
            onClick={() => onTypeChange('STORE')}
          >
            상점 정보
          </FilterBtn>
        </ButtonGroup>
      </FilterRow>
    </FilterCard>
  );
}
