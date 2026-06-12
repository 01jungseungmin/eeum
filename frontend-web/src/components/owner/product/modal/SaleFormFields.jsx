import React from 'react';
import styled from 'styled-components';

const RowGroup = styled.div`
  display: flex;
  gap: 15px;
  margin-bottom: 20px;
  & > div {
    flex: 1;
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
const OptionHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 25px;
  margin-bottom: 10px;
  .add-btn {
    color: #00a651;
    font-size: 13px;
    font-weight: bold;
    cursor: pointer;
    border: none;
    background: none;
  }
`;
const EmptyOptionBox = styled.div`
  border: 1px dashed #e9ecef;
  border-radius: 10px;
  padding: 20px;
  text-align: center;
  color: #adb5bd;
  font-size: 13px;
  margin-bottom: 30px;
`;

function SaleFormFields({
  categoryId,
  setCategoryId,
  basePrice,
  setBasePrice,
  stockQuantity,
  setStockQuantity,
}) {
  return (
    <>
      <RowGroup>
        <FormGroup>
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
        </FormGroup>
        <FormGroup>
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
        </FormGroup>
      </RowGroup>

      <FormGroup>
        <Label>재고 수량 (선택)</Label>
        <Input
          type="number"
          placeholder="미설정 시 비워두세요"
          value={stockQuantity}
          onChange={(e) => setStockQuantity(e.target.value)}
        />
      </FormGroup>

      <div>
        <OptionHeader>
          <Label style={{ margin: 0 }}>상품 옵션</Label>
          <button type="button" className="add-btn">
            ＋ 옵션 추가
          </button>
        </OptionHeader>
        <EmptyOptionBox>옵션 없음 (단일 상품)</EmptyOptionBox>
      </div>
    </>
  );
}

export default SaleFormFields;
