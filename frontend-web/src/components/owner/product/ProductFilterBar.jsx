// 📄 src/pages/owner/main/components/ProductFilterBar.jsx

import React from 'react';
import styled from 'styled-components';
import { Search, Plus, ChevronDown } from 'lucide-react';

// 시안 특유의 대형 라운드 박스 형태 유지
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

// 🟢 1번 그룹 전용: 활성화 시 밝은 에메랄드 그린 (시안 '전체' 버튼 색상)
const StatusButton = styled(PillButton)`
  ${(props) =>
    props.$active &&
    `
      background: #4cb184;
      color: #ffffff;
      border-color: #4cb184;
    `}
`;

// 🌲 2번 그룹 전용: 활성화 시 딥 다크 그린 (시안 '전체유형' 버튼 색상)
const TypeButton = styled(PillButton)`
  ${(props) =>
    props.$active &&
    `
      background: #1e3d2f;
      color: #ffffff;
      border-color: #1e3d2f;
    `}
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
}) {
  return (
    <FilterBarContainer>
      {/* 1. 상품명, 카테고리 검색창 */}
      <SearchWrapper>
        <Search className="search-icon" size={18} />
        <SearchInput
          type="text"
          placeholder="상품명, 카테고리 검색..."
          value={searchTerm}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </SearchWrapper>

      {/* 2. 첫 번째 판매 상태 그룹 (Bright Green 계열 적용) */}
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
          $active={statusFilter === 'INACTIVE'}
          onClick={() => onStatusChange('INACTIVE')}
        >
          품절
        </StatusButton>
        <StatusButton
          $active={statusFilter === 'HIDDEN'}
          onClick={() => onStatusChange('HIDDEN')}
        >
          비공개
        </StatusButton>
      </ButtonGroup>

      {/* 3. 두 번째 상품 유형 그룹 (Dark Green 계열 적용) */}
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
          $active={typeFilter === 'PREORDER'}
          onClick={() => onTypeChange('PREORDER')}
        >
          예약
        </TypeButton>
        <TypeButton
          $active={typeFilter === 'MENU'}
          onClick={() => onTypeChange('MENU')}
        >
          메뉴
        </TypeButton>
      </ButtonGroup>

      {/* 4. 일괄 관리 옵션 드롭다운 */}
      <DropdownButton onClick={() => alert('일괄 관리 기능 준비 중입니다.')}>
        <span>일괄 관리</span>
        <ChevronDown size={16} />
      </DropdownButton>

      {/* 5. 우측 상품 등록 버튼 */}
      <RegisterButton onClick={onOpenRegisterModal}>
        <Plus size={16} strokeWidth={2.5} />
        <span>상품 등록</span>
      </RegisterButton>
    </FilterBarContainer>
  );
}

export default ProductFilterBar;
