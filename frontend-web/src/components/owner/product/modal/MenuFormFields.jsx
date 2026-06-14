import React from 'react';
import styled from 'styled-components';

// 가로로 나란히 배치하기 위한 정렬 컴포넌트 추가
const RowGroup = styled.div`
  display: flex;
  gap: 12px;
  margin-bottom: 20px;

  & > div {
    flex: 1; /* 카테고리와 가격 영역을 5:5 비율로 채웁니다 */
  }
`;

const FormGroup = styled.div`
  margin-bottom: 20px;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: bold;
  color: #333;
  margin-bottom: 8px;
  display: block;
  span {
    color: #ff4d4d;
    margin-left: 2px;
  }
`;

const Select = styled.select`
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e9ecef;
  border-radius: 10px;
  font-size: 13px;
  outline: none;
  background: #fff;
  box-sizing: border-box;
  &:focus {
    border-color: #00a651;
  }
`;

const Input = styled.input`
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e9ecef;
  border-radius: 10px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  &:focus {
    border-color: #00a651;
  }
`;

function MenuFormFields({
  categoryId,
  setCategoryId,
  basePrice,
  setBasePrice,
  stockQuantity,
}) {
  return (
    <>
      <RowGroup>
        <div>
          <Label>
            카테고리 <span>*</span>
          </Label>
          <Select
            required
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">선택</option>
            <option value="1">국/찌개</option>
            <option value="2">반찬류</option>
          </Select>
        </div>

        <div>
          <Label>
            기본 가격 <span>*</span> (원)
          </Label>
          <Input
            type="number"
            placeholder="0"
            required
            value={basePrice}
            onChange={(e) => setBasePrice(e.target.value)}
          />
        </div>
      </RowGroup>
    </>
  );
}

export default MenuFormFields;
