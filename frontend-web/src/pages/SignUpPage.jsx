import styled from 'styled-components';
import InputForm from '../components/InputForm';

const PageWrapper = styled.div`
  max-width: 450px;
  margin: 60px auto;
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
  color: #00a651;
`;

const CheckboxContainer = styled.div`
  width: 100%;
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
  font-size: 13px;
  color: #666;
`;

const SubmitButton = styled.button`
  width: 100%;
  padding: 15px;
  background-color: #00a651;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  margin-top: 30px;
`;

function SignUp() {
  return (
    <PageWrapper>
      <Title>EEUM</Title>
      <SubTitle>사장님 가입하기</SubTitle>

      <InputForm
        title="이메일"
        placeholder="이메일을 입력해주세요"
        buttonText="인증받기"
      />
      <InputForm title="인증코드" placeholder="인증코드를 입력해주세요" />
      <InputForm
        title="비밀번호"
        type="password"
        placeholder="비밀번호를 입력해주세요"
      />
      <InputForm
        title="비밀번호 확인"
        type="password"
        placeholder="비밀번호를 다시 입력해주세요"
      />
      <InputForm title="사업자번호" placeholder="- 제외하고 입력해주세요" />
      <InputForm title="사업자명" placeholder="사업자명을 입력해주세요" />
      <InputForm title="상호명" placeholder="상호명을 입력해주세요" />
      <InputForm
        title="사업장 소재지"
        placeholder="사업장 소재지를 입력해주세요"
      />
      <InputForm
        title="사업장 전화번호"
        placeholder="사업장 전화번호를 입력해주세요"
      />

      <CheckboxContainer>
        <label>
          <input type="checkbox" /> [필수] 이용약관에 동의합니다.
        </label>
        <label>
          <input type="checkbox" /> [필수] 개인정보 수집 및 이용에 동의합니다.
        </label>
      </CheckboxContainer>

      <SubmitButton>가입하기</SubmitButton>
    </PageWrapper>
  );
}

export default SignUp;
