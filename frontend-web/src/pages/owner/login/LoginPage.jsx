import styled from 'styled-components';
import InputForm from '../../../components/InputForm';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../../../contexts/AuthContext';
import axios from 'axios';

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

function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleLogin = async (e) => {
    e.preventDefault();

    try {
      const response = await axios.post('http://localhost:8080/auth/login', {
        email,
        password,
      });

      const { success, data, message } = response.data;

      if (success) {
        login(data.accessToken, data.role, data.refreshToken);

        alert(message);

        const redirectPath =
          data.role === 'ROLE_ADMIN' ? '/admin/dashboard' : '/approval-status';

        navigate(redirectPath);
      } else {
        alert(response.data.error?.message || '로그인에 실패했습니다.');
      }
    } catch (error) {
      console.error('로그인 에러:', error);
      alert('로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.');
    }
  };

  return (
    <PageWrapper>
      <Title>EEUM</Title>
      <SubTitle>로그인</SubTitle>

      <InputForm
        title="이메일"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="이메일을 입력해주세요"
      />
      <InputForm
        title="비밀번호"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="비밀번호를 입력해주세요"
      />

      <OptionsRow>
        <label style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
          <input type="checkbox" /> 자동 로그인
        </label>
        <span
          onClick={() => navigate('/find-password')}
          style={{ cursor: 'pointer' }}
        >
          비밀번호 찾기
        </span>
      </OptionsRow>

      <LoginButton onClick={handleLogin}>로그인</LoginButton>
      <div
        onClick={() => navigate('/sign-up')}
        style={{ fontSize: '13px', color: '#666', cursor: 'pointer' }}
      >
        회원가입
      </div>
    </PageWrapper>
  );
}

export default LoginPage;
