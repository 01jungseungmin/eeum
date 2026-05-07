import styled from 'styled-components';
import InputForm from '../components/InputForm';

const PageWrapper = styled.div`
  max-width: 400px;
  margin: 100px auto;
  display: flex;
  flex-direction: column;
  align-items: center;
`;

const Title = styled.h1`
  color: #00a651;
  font-size: 28px;
  margin-bottom: 0;
`;
const SubTitle = styled.p`
  margin-top: 5px;
  margin-bottom: 40px;
  font-weight: bold;
`;

const ChangeButton = styled.button`
  width: 100%;
  padding: 15px;
  background-color: #00a651;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  margin-top: 20px;
`;

function FindPassword() {
  return (
    <PageWrapper>
      <Title>EEUM</Title>
      <SubTitle>비밀번호 찾기</SubTitle>

      <InputForm
        title="이메일"
        placeholder="이메일을 입력해주세요"
        buttonText="인증받기"
      />
      <InputForm title="인증코드" placeholder="인증코드를 입력해주세요" />
      <InputForm
        title="새로운 비밀번호"
        placeholder="비밀번호를 입력해주세요"
        type="password"
      />
      <InputForm
        title="새로운 비밀번호 확인"
        placeholder="비밀번호를 다시 입력해주세요"
        type="password"
      />

      <ChangeButton>변경하기</ChangeButton>
    </PageWrapper>
  );
}

export default FindPassword;
