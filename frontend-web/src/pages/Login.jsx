import styled from 'styled-components';
import kakaoIcon from '../assets/kakao_icon.png';
import naverIcon from '../assets/naver_icon.png';
import InputForm from '../components/InputForm';

const PageWrapper = styled.div`
  max-width: 400px;
  min-height: 100vh;
  margin: 0px auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
`;

const Title = styled.h1`
  color: #009e60;
  font-size: 24px;
  margin-bottom: 0px;
`;

const SubTitle = styled.p`
  margin-top: 5px;
  margin-bottom: 30px;
  color: #009e60;
  font-size: 16px;
  font-weight: bold;
`;

const LoginButton = styled.button`
  width: 100%;
  padding: 12px;
  background-color: #009e60;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  margin-bottom: 15px;
`;

const OptionsRow = styled.div`
  width: 100%;
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #666;
  margin-bottom: 25px;
`;

const Divider = styled.div`
  display: flex;
  align-items: center;
  width: 100%;
  margin: 20px 0;
  color: #888;
  font-size: 12px;

  /* 왼쪽 선 */
  &::before {
    content: '';
    flex: 1;
    height: 1px;
    background: #e0e0e0;
    margin-right: 10px;
  }

  /* 오른쪽 선 */
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: #e0e0e0;
    margin-left: 10px;
  }
`;

const SocialLogin = styled.div`
  margin-top: 30px;
  display: flex;
  gap: 20px;
`;

const SocialCircle = styled.div`
  width: 45px;
  height: 45px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
`;

function LoginPage() {
  return (
    <PageWrapper>
      <Title>EEUM</Title>
      <SubTitle>로그인</SubTitle>

      <InputForm title="이메일" placeholder="이메일을 입력해주세요" />
      <InputForm
        title="비밀번호"
        type="password"
        placeholder="비밀번호를 입력해주세요"
      />

      <OptionsRow>
        <label style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
          <input type="checkbox" /> 자동 로그인
        </label>
        <span>비밀번호 찾기</span>
      </OptionsRow>

      <LoginButton>로그인</LoginButton>
      <div style={{ fontSize: '13px', color: '#666', cursor: 'pointer' }}>
        회원가입
      </div>
      <Divider>간편 로그인</Divider>

      <SocialLogin>
        <SocialCircle>
          <img src={kakaoIcon} alt="카카오 로그인" />
        </SocialCircle>
        <SocialCircle>
          <img src={naverIcon} alt="네이버 로그인" />
        </SocialCircle>
      </SocialLogin>
    </PageWrapper>
  );
}

export default LoginPage;
