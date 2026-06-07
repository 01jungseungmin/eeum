// 📄 src/pages/owner/product/components/ProductFilterBar.jsx
import styled from 'styled-components';

const FilterWrapper = styled.div`
  background: white;
  padding: 15px 20px;
  border-radius: 12px;
  border: 1px solid #eef0f2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
`;

const LeftFilters = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
`;

const SearchInput = styled.input`
  padding: 10px 15px;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  font-size: 13px;
  width: 280px;
  background-color: #f8f9fa;
`;

const FilterButtonGroup = styled.div`
  display: flex;
  gap: 6px;
  border-right: 1px solid #eee;
  padding-right: 12px;
  &:last-child {
    border-right: none;
    padding-right: 0;
  }
`;

const FilterButton = styled.button`
  padding: 8px 14px;
  border: 1px solid ${(props) => (props.$active ? 'transparent' : '#e9ecef')};
  background: ${(props) =>
    props.$active ? (props.$dark ? '#222' : '#00a651') : '#fff'};
  color: ${(props) => (props.$active ? '#fff' : '#555')};
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  font-weight: ${(props) => (props.$active ? 'bold' : 'normal')};
`;

const RightActions = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
`;

const SelectBox = styled.select`
  padding: 10px 12px;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  font-size: 13px;
  color: #555;
  outline: none;
  background-color: #fff;
`;

const RegisterButton = styled.button`
  padding: 10px 16px;
  background-color: #00a651;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  &:hover {
    background-color: #008441;
  }
`;

function ProductFilterBar() {
  return (
    <FilterWrapper>
      <LeftFilters>
        <SearchInput placeholder="🔍 상품명, 카테고리 검색..." />

        {/* 판매상태 필터 그룹 */}
        <FilterButtonGroup>
          <FilterButton $active>전체</FilterButton>
          <FilterButton>판매중</FilterButton>
          <FilterButton>품절</FilterButton>
          <FilterButton>비공개</FilterButton>
        </FilterButtonGroup>

        {/* 유형 필터 그룹 */}
        <FilterButtonGroup>
          <FilterButton $active $dark>
            전체유형
          </FilterButton>
          <FilterButton>판매</FilterButton>
          <FilterButton>예약</FilterButton>
          <FilterButton>메뉴</FilterButton>
        </FilterButtonGroup>
      </LeftFilters>

      <RightActions>
        <SelectBox>
          <option>일괄 관리</option>
        </SelectBox>
        <RegisterButton>＋ 상품 등록</RegisterButton>
      </RightActions>
    </FilterWrapper>
  );
}

export default ProductFilterBar;
