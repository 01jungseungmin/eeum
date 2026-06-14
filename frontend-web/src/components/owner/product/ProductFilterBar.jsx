import React, { useState } from 'react';
import styled from 'styled-components';
import { Search, Plus, ChevronDown } from 'lucide-react';

const FilterBarContainer = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  background: #ffffff;
  padding: 18px 24px;
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  margin-bottom: 24px;
`;

// 검색창 영역
const SearchWrapper = styled.div`
  position: relative;
  flex: 1;
  max-width: 320px;

  .search-icon {
    position: absolute;
    left: 16px;
    top: 50%;
    transform: translateY(-50%);
    color: #8e94a0;
  }
`;

const SearchInput = styled.input`
  width: 100%;
  padding: 12px 16px 12px 44px;
  border: 1px solid #f1f3f5;
  background-color: #f8f9fa;
  border-radius: 30px;
  font-size: 14px;
  outline: none;
  &::placeholder {
    color: #adb5bd;
  }
`;

// 일반 필터 그룹 공통 래퍼
const ButtonGroup = styled.div`
  display: flex;
  gap: 6px;
`;

// 공통 기본 알약 버튼 스타일
const PillButton = styled.button`
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 30px;
  border: 1px solid #e9ecef;
  background: #ffffff;
  color: #495057;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
`;

const StatusButton = styled(PillButton)`
  ${(props) =>
    props.$active &&
    `
      background: #4cb184;
      color: #ffffff;
      border-color: #4cb184;
    `}
`;

const TypeButton = styled(PillButton)`
  ${(props) =>
    props.$active &&
    `
      background: #1e3d2f;
      color: #ffffff;
      border-color: #1e3d2f;
    `}
`;

const DropdownContainer = styled.div`
  position: relative;
  display: inline-block;
`;

const DropdownMenu = styled.div`
  position: absolute;
  top: calc(100% + 5px);
  right: 0;
  background: white;
  min-width: 160px;
  border-radius: 12px;
  border: 1px solid #eef0f2;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  padding: 8px 0;
  z-index: 100;
  text-align: left;

  .select-count {
    padding: 8px 16px;
    font-size: 12px;
    color: #8e94a0;
    border-bottom: 1px solid #f1f3f5;
    margin-bottom: 4px;
    font-weight: 500;
  }

  button {
    width: 100%;
    background: none;
    border: none;
    padding: 10px 16px;
    font-size: 13px;
    text-align: left;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;
    transition: background 0.2s;

    &:hover {
      background: #f8f9fa;
    }

    &.activate {
      color: #00a651;
      font-weight: 500;
    }
    &.soldout {
      color: #e63946;
      font-weight: 500;
    }
    &.delete {
      color: #666666;
    }
    &.close {
      color: #999;
      border-top: 1px solid #f1f3f5;
      margin-top: 4px;
      font-size: 12px;
    }
  }
`;

// 일괄 관리 선택창
const DropdownButton = styled(PillButton)`
  display: flex;
  align-items: center;
  gap: 6px;
  background: #ffffff;
  color: #495057;
`;

// 상품 등록 버튼 (우측 정렬용 마진 자동 배치 및 색감 통일)
const RegisterButton = styled.button`
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 22px;
  background: #4cb184;
  color: #ffffff;
  border: none;
  border-radius: 30px;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(76, 177, 132, 0.2);
  &:hover {
    background: #3fa175;
  }
`;

function ProductFilterBar({
  searchTerm,
  onSearchChange,
  statusFilter,
  onStatusChange,
  typeFilter,
  onTypeChange,
  onOpenRegisterModal,
  selectedIds,
  onBulkStatusChange,
  onBulkDelete,
}) {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  return (
    <FilterBarContainer>
      <SearchWrapper>
        <Search className="search-icon" size={18} />
        <SearchInput
          type="text"
          placeholder="상품명, 카테고리 검색..."
          value={searchTerm}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </SearchWrapper>

      <ButtonGroup>
        <StatusButton
          $active={statusFilter === 'ALL'}
          onClick={() => onStatusChange('ALL')}
        >
          전체
        </StatusButton>
        <StatusButton
          $active={statusFilter === 'ACTIVE'}
          onClick={() => onStatusChange('ACTIVE')}
        >
          판매중
        </StatusButton>
        <StatusButton
          $active={statusFilter === 'SOLD_OUT'}
          onClick={() => onStatusChange('SOLD_OUT')}
        >
          품절
        </StatusButton>
        <StatusButton
          $active={statusFilter === 'INACTIVE'}
          onClick={() => onStatusChange('INACTIVE')}
        >
          비공개
        </StatusButton>
      </ButtonGroup>

      <ButtonGroup>
        <TypeButton
          $active={typeFilter === 'ALL'}
          onClick={() => onTypeChange('ALL')}
        >
          전체유형
        </TypeButton>
        <TypeButton
          $active={typeFilter === 'SALE'}
          onClick={() => onTypeChange('SALE')}
        >
          판매
        </TypeButton>
        <TypeButton
          $active={typeFilter === 'MENU'}
          onClick={() => onTypeChange('MENU')}
        >
          메뉴
        </TypeButton>
      </ButtonGroup>

      <DropdownContainer>
        <DropdownButton onClick={() => setIsDropdownOpen(!isDropdownOpen)}>
          <span>일괄 관리</span>
          <ChevronDown size={16} />
        </DropdownButton>

        {isDropdownOpen && (
          <DropdownMenu>
            <div className="select-count">{selectedIds.length}개 선택됨</div>

            <button
              type="button"
              className="activate"
              onClick={() => {
                onBulkStatusChange('ACTIVE');
                setIsDropdownOpen(false); // 실행 후 드롭다운 닫기
              }}
            >
              <span>✓ 일괄 판매 활성화</span>
            </button>

            <button
              type="button"
              className="soldout"
              onClick={() => {
                onBulkStatusChange('SOLD_OUT');
                setIsDropdownOpen(false);
              }}
            >
              <span>✕ 일괄 품절 처리</span>
            </button>

            <button
              type="button"
              className="delete"
              onClick={() => {
                onBulkDelete();
                setIsDropdownOpen(false);
              }}
            >
              <span>🗑️ 일괄 삭제</span>
            </button>

            <button
              type="button"
              className="close"
              onClick={() => setIsDropdownOpen(false)}
            >
              닫기
            </button>
          </DropdownMenu>
        )}
      </DropdownContainer>

      <RegisterButton onClick={onOpenRegisterModal}>
        <Plus size={16} strokeWidth={2.5} />
        <span>상품 등록</span>
      </RegisterButton>
    </FilterBarContainer>
  );
}

export default ProductFilterBar;
