import React, { useState } from 'react';
import styled from 'styled-components';
import { Search, ChevronDown } from 'lucide-react';

// 1번째 사진처럼 전체 영역을 감싸는 백그라운드 컨테이너 스타일링
const FilterBarOuter = styled.div`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  margin-bottom: 16px;
`;

const BarContainer = styled.div`
  display: flex;
  gap: 12px;
  position: relative;
`;

const SearchWrapper = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  background: #f3f4f6; /* 사진 속 흐린 회색 인풋 배경색 적용 */
  border-radius: 24px; /* 라운드 스타일 */
  padding: 0 16px;
  gap: 8px;
  height: 40px;
`;

const Input = styled.input`
  border: none;
  outline: none;
  background: transparent;
  width: 100%;
  font-size: 13px;
  color: #1f2937;
  &::placeholder {
    color: #9ca3af;
  }
`;

const DropdownContainer = styled.div`
  position: relative;
`;

const DropdownButton = styled.button`
  background: #f3f4f6; /* 인풋창과 톤앤매너 일치 */
  border: none;
  border-radius: 8px;
  padding: 0 16px;
  height: 40px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  min-width: 100px;
  justify-content: space-between;
`;

// 2번째 사진 크롭본 기준: 어두운 챠콜 그레이 바탕 레이아웃 적용
const Menu = styled.div`
  position: absolute;
  right: 0;
  top: 100%;
  margin-top: 6px;
  background: #555555; /* 사진 속 다크 그레이 컬러 */
  border-radius: 12px;
  padding: 6px;
  min-width: 140px;
  z-index: 50;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
`;

const Item = styled.div`
  padding: 10px 14px;
  font-size: 13px;
  border-radius: 8px;
  color: #ffffff;
  font-weight: 500;
  display: flex;
  align-items: center;
  cursor: pointer;

  /* 2번째 사진 속 선택된 아이템 블루 하이라이트 스타일 구현 */
  background: ${(props) => (props.$selected ? '#5c8cee' : 'transparent')};

  &:hover {
    background: ${(props) =>
      props.$selected ? '#4b7be5' : 'rgba(255, 255, 255, 0.1)'};
  }
`;

function OrderFilterBar({
  filterType,
  setFilterType,
  searchTerm,
  setSearchTerm,
}) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <FilterBarOuter>
      <BarContainer>
        <SearchWrapper>
          <Search size={16} color="#9ca3af" />
          <Input
            type="text"
            placeholder="주문번호, 고객명, 상품명 검색..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </SearchWrapper>

        <DropdownContainer>
          <DropdownButton onClick={() => setIsOpen(!isOpen)}>
            {filterType} <ChevronDown size={14} color="#4b5563" />
          </DropdownButton>

          {isOpen && (
            <Menu>
              {['전체 유형', '구매 주문', '방문 예약'].map((type) => (
                <Item
                  key={type}
                  $selected={filterType === type}
                  onClick={() => {
                    setFilterType(type);
                    setIsOpen(false);
                  }}
                >
                  {/* 피그마 드롭다운 시안용 가상 체크 표시 대응 처리 */}
                  {filterType === type && (
                    <span style={{ marginRight: '6px' }}>✓</span>
                  )}
                  {type}
                </Item>
              ))}
            </Menu>
          )}
        </DropdownContainer>
      </BarContainer>
    </FilterBarOuter>
  );
}

export default OrderFilterBar;
