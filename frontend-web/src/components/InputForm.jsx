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
`;

const StyledInput = styled.input`
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd; /* 더 연한 회색 */
  border-radius: 4px;
  box-sizing: border-box; /* 패딩이 너비에 포함되게 */
  &::placeholder {
    color: #ccc;
  }
`;

function InputForm({ title, type = 'text', placeholder }) {
  return (
    <Container>
      <Label>{title}</Label>
      <StyledInput type={type} placeholder={placeholder} />
    </Container>
  );
}

export default InputForm;
