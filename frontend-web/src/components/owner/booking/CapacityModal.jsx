import React from 'react';
import styled from 'styled-components';
import { X } from 'lucide-react';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: flex-end; /* 우측 사이드 팝업 형태인 경우 사용, 중앙 배치는 center */
  align-items: center;
  z-index: 1000;
`;

const ModalContainer = styled.div`
  background: #fff;
  width: 440px;
  height: 100vh;
  padding: 24px;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.1);
  font-family: 'Noto Sans KR', sans-serif;
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  h2 {
    font-size: 18px;
    font-weight: 700;
    color: #1a1a1a;
  }
  button {
    background: none;
    border: none;
    cursor: pointer;
    color: #868e96;
  }
`;

const Subtitle = styled.p`
  font-size: 12px;
  color: #999;
  margin-bottom: 24px;
`;

const ScrollArea = styled.div`
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  margin-bottom: 20px;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #e9ecef;
    border-radius: 3px;
  }
`;

const InputRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
`;

const TimeText = styled.span`
  font-size: 14px;
  font-weight: 700;
  color: #495057;
  width: 80px;
`;

const InputGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;

  span {
    font-size: 14px;
    color: #495057;
  }
  button.delete-btn {
    background: none;
    border: none;
    color: #ced4da;
    cursor: pointer;
    font-size: 16px;
  }
`;

const StyledInput = styled.input`
  width: 80px;
  padding: 8px 12px;
  border: 1px solid #ced4da;
  border-radius: 8px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;

  &:focus {
    outline: none;
    border-color: #4ca771;
  }
`;

const FooterGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
`;

const FooterBtn = styled.button`
  padding: 14px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid ${(props) => (props.primary ? '#4CA771' : '#e9ecef')};
  background: ${(props) => (props.primary ? '#4CA771' : '#fff')};
  color: ${(props) => (props.primary ? '#fff' : '#495057')};
`;

export default function CapacityModal({ onClose }) {
  const timeSlots = [
    '10:00',
    '10:30',
    '11:00',
    '11:30',
    '12:00',
    '12:30',
    '14:00',
    '14:30',
    '15:00',
    '15:30',
    '16:00',
    '16:30',
    '17:00',
    '17:30',
  ];

  return (
    <Overlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <h2>시간대별 수용인원 설정</h2>
          <button onClick={onClose}>
            <X size={20} />
          </button>
        </ModalHeader>
        <Subtitle>
          2026-04-29 · 각 시간대에 받을 수 있는 최대 인원을 설정하세요
        </Subtitle>

        <ScrollArea>
          {timeSlots.map((time) => (
            <InputRow key={time}>
              <TimeText>{time}</TimeText>
              <InputGroup>
                <StyledInput type="number" defaultValue={5} />
                <span>명</span>
                <button className="delete-btn">×</button>
              </InputGroup>
            </InputRow>
          ))}
        </ScrollArea>

        <FooterGrid>
          <FooterBtn onClick={onClose}>취소</FooterBtn>
          <FooterBtn primary onClick={onClose}>
            저장
          </FooterBtn>
        </FooterGrid>
      </ModalContainer>
    </Overlay>
  );
}
