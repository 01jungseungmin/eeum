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

function ReservationFormFields({
  categoryId,
  setCategoryId,
  reservationCapacity,
  setReservationCapacity,
}) {
  return (
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
          <option value="3">도시락 예약</option>
          <option value="4">정기 배송</option>
        </Select>
      </FormGroup>
      <FormGroup>
        <Label>예약 인원 (선택)</Label>
        <Input
          type="number"
          placeholder="최대 인원 (미설정 가능)"
          value={reservationCapacity}
          onChange={(e) => setReservationCapacity(e.target.value)}
        />
      </FormGroup>
    </RowGroup>
  );
}

export default ReservationFormFields;
