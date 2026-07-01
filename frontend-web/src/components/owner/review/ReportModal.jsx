import React, { useState } from 'react';
import styled from 'styled-components';

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
`;

const ModalContainer = styled.div`
  background: white;
  border-radius: 16px;
  width: 440px;
  padding: 32px;
  position: relative;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
`;

const CloseButton = styled.button`
  position: absolute;
  top: 20px;
  right: 20px;
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #9ca3af;
`;

const Title = styled.h2`
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8px;
`;

const Notice = styled.p`
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
  margin-bottom: 20px;
`;

const ReasonList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
`;

const ReasonItem = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid ${(props) => (props.active ? '#ffedd5' : '#e5e7eb')};
  background-color: ${(props) => (props.active ? '#fff7ed' : '#ffffff')};
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
`;

const RadioCircle = styled.div`
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid ${(props) => (props.active ? '#f97316' : '#9ca3af')};
  background: ${(props) => (props.active ? '#f97316' : 'transparent')};
  position: relative;

  &::after {
    content: '';
    position: absolute;
    inset: 4px;
    background: white;
    border-radius: 50%;
    display: ${(props) => (props.active ? 'block' : 'none')};
  }
`;

const StyledTextArea = styled.textarea`
  width: 100%;
  height: 80px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  resize: none;
  font-size: 13px;
  outline: none;
  margin-bottom: 24px;
  &:focus {
    border-color: #f97316;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 12px;
`;

const CancelButton = styled.button`
  flex: 1;
  padding: 14px;
  border: 1px solid #e5e7eb;
  background: white;
  border-radius: 8px;
  cursor: pointer;
`;

const SubmitButton = styled.button`
  flex: 1;
  padding: 14px;
  background: #ffedd5;
  color: #ea580c;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  &:hover {
    background: #fed7aa;
  }
`;

const REPORT_REASONS = [
  '악의적/허위 리뷰',
  '욕설/비방/모욕',
  '영업 방해 목적',
  '개인정보 포함',
  '경쟁업체 광고',
  '기타',
];

const ReportModal = ({ reviewId, onClose }) => {
  const [selectedReason, setSelectedReason] = useState('');
  const [description, setDescription] = useState('');

  const handleSubmit = () => {
    if (!selectedReason) {
      alert('신고 사유를 선택해주세요.');
      return;
    }
    // API 전송 로직이 들어갈 자리
    alert(`리뷰 ID [${reviewId}]가 '${selectedReason}' 사유로 접수되었습니다.`);
    onClose();
  };

  return (
    <Overlay>
      <ModalContainer>
        <CloseButton onClick={onClose}>&times;</CloseButton>
        <Title>악성 리뷰 신고</Title>
        <Notice>
          이음 운영팀에서 검토 후 처리 결과를 안내드립니다. 허위 신고 시 이용이
          제한될 수 있습니다.
        </Notice>

        <ReasonList>
          {REPORT_REASONS.map((reason) => (
            <ReasonItem
              key={reason}
              active={selectedReason === reason}
              onClick={() => setSelectedReason(reason)}
            >
              <RadioCircle active={selectedReason === reason} />
              {reason}
            </ReasonItem>
          ))}
        </ReasonList>

        <StyledTextArea
          placeholder="추가 설명 (선택 사항)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <ButtonGroup>
          <CancelButton onClick={onClose}>취소</CancelButton>
          <SubmitButton onClick={handleSubmit}>신고 접수</SubmitButton>
        </ButtonGroup>
      </ModalContainer>
    </Overlay>
  );
};

export default ReportModal;
