import React from 'react';
import styled from 'styled-components';

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

function MenuFormFields({ categoryId, setCategoryId }) {
  return (
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
  );
}

export default MenuFormFields;
