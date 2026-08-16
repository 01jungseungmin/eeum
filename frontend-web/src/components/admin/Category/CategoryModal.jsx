import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { X } from 'lucide-react';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background: #ffffff;
  width: 100%;
  max-width: 440px;
  border-radius: 16px;
  padding: 28px;
  box-shadow:
    0 20px 25px -5px rgba(0, 0, 0, 0.1),
    0 8px 10px -6px rgba(0, 0, 0, 0.1);
  position: relative;
  box-sizing: border-box;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 24px;
  right: 24px;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;

  &:hover {
    color: #0f172a;
  }
`;

const Title = styled.h2`
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px 0;
`;

const Subtitle = styled.p`
  font-size: 14px;
  color: #64748b;
  margin: 0 0 24px 0;
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 28px;
`;

const Label = styled.label`
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
`;

const Input = styled.input`
  width: 100%;
  height: 48px;
  background-color: #f4f4f5;
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  padding: 0 16px;
  font-size: 14px;
  color: #0f172a;
  box-sizing: border-box;
  outline: none;

  &::placeholder {
    color: #a1a1aa;
  }

  &:focus {
    border-color: #00b074;
    background-color: #ffffff;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 10px;
`;

const CancelButton = styled.button`
  height: 40px;
  padding: 0 18px;
  background-color: #ffffff;
  border: 1px solid #e4e4e7;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  color: #3f3f46;
  cursor: pointer;

  &:hover {
    background-color: #f4f4f5;
  }
`;

const SubmitButton = styled.button`
  height: 40px;
  padding: 0 20px;
  background-color: #00b074;
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #009663;
  }

  &:disabled {
    background-color: #a7f3d0;
    cursor: not-allowed;
  }
`;

export default function CategoryModal({
  isOpen,
  mode = 'ADD', // 'ADD' | 'EDIT'
  initialValue = '',
  onClose,
  onSubmit,
}) {
  const [categoryName, setCategoryName] = useState('');

  useEffect(() => {
    if (isOpen) {
      setCategoryName(initialValue);
    }
  }, [isOpen, initialValue]);

  if (!isOpen) return null;

  const isEdit = mode === 'EDIT';

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!categoryName.trim()) return;
    onSubmit(categoryName.trim());
  };

  return (
    <Overlay onClick={onClose}>
      <ModalBox onClick={(e) => e.stopPropagation()}>
        <CloseButton onClick={onClose}>
          <X size={20} />
        </CloseButton>

        <Title>{isEdit ? '카테고리 수정' : '카테고리 추가'}</Title>
        <Subtitle>
          {isEdit
            ? '카테고리 이름을 수정합니다.'
            : '새로운 카테고리를 추가합니다.'}
        </Subtitle>

        <form onSubmit={handleSubmit}>
          <FormGroup>
            <Label>카테고리 이름</Label>
            <Input
              type="text"
              placeholder="카테고리 이름 입력"
              value={categoryName}
              onChange={(e) => setCategoryName(e.target.value)}
              autoFocus
            />
          </FormGroup>

          <ButtonGroup>
            <CancelButton
              type="button"
              onClick={onClose}
            >
              취소
            </CancelButton>
            <SubmitButton
              type="submit"
              disabled={!categoryName.trim()}
            >
              {isEdit ? '수정' : '추가'}
            </SubmitButton>
          </ButtonGroup>
        </form>
      </ModalBox>
    </Overlay>
  );
}
