import styled from 'styled-components';

const Container = styled.div`
  width: 100%;
  margin-bottom: 20px;
`;

const Label = styled.label`
  display: block;
  font-size: 14px;
  font-weight: bold;
  margin-bottom: 8px;
  text-align: left;
`;

const InputWrapper = styled.div`
  display: flex;
  gap: 10px; /* 인풋과 버튼 사이 간격 */
`;

const StyledInput = styled.input`
  flex: 1; /* 남은 공간 꽉 채우기 */
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  &::placeholder {
    color: #ccc;
  }
`;

const ActionButton = styled.button`
  padding: 0 15px;
  background-color: #00a651;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap; /* 글자 줄바꿈 방지 */
  &:hover {
    background-color: #008441;
  }
`;

function InputForm({
  title,
  type = 'text',
  placeholder,
  buttonText,
  onButtonClick,
}) {
  return (
    <Container>
      <Label>{title}</Label>
      <InputWrapper>
        <StyledInput type={type} placeholder={placeholder} />
        {buttonText && (
          <ActionButton type="button" onClick={onButtonClick}>
            {buttonText}
          </ActionButton>
        )}
      </InputWrapper>
    </Container>
  );
}

export default InputForm;
