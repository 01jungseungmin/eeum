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
  gap: 10px;
`;

const StyledInput = styled.input`
  flex: 1;
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
  white-space: nowrap;
  &:hover {
    background-color: #008441;
  }
`;

function InputForm({
  title,
  type = 'text',
  placeholder,
  value,
  onChange,
  buttonText,
  onButtonClick,
}) {
  return (
    <Container>
      <Label>{title}</Label>
      <InputWrapper>
        <StyledInput
          type={type}
          placeholder={placeholder}
          value={value}
          onChange={onChange}
        />
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
