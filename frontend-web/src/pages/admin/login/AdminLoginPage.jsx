import React, { useState } from 'react';
import styled from 'styled-components';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../contexts/AuthContext';
import { authApi } from '../../../api/authApi';

const LoginWrapper = styled.div`
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 107, 84);
  padding: 16px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial,
    sans-serif;
`;

const LoginCard = styled.div`
  width: 100%;
  max-width: 440px;
  background: #ffffff;
  border-radius: 24px;
  padding: 40px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.05);
  text-align: center;
`;

const LoginLogo = styled.div`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  background-color: #006b54;
  color: #ffffff;
  font-size: 28px;
  font-weight: bold;
  border-radius: 16px;
  margin-bottom: 24px;
`;

const Title = styled.h2`
  font-size: 28px;
  font-weight: 600;
  color: #0c0a09;
  margin: 0 0 8px 0;
`;

const Subtitle = styled.p`
  color: #6b7280;
  margin-bottom: 32px;
  font-weight: 500;
  font-size: 15px;
`;

const InputGroup = styled.div`
  text-align: left;
  margin-bottom: 20px;

  label {
    display: block;
    font-size: 14px;
    font-weight: 500;
    color: #374151;
    margin-bottom: 8px;
  }

  input {
    width: 100%;
    padding: 14px 16px;
    background-color: #f8f9fa;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    font-size: 15px;
    box-sizing: border-box;
    transition: all 0.2s ease;
    color: #0c0a09;

    &:focus {
      outline: none;
      border-color: #006b54;
      background-color: #ffffff;
      box-shadow: 0 0 0 2px rgba(0, 107, 84, 0.15);
    }
  }
`;

const SubmitButton = styled.button`
  width: 100%;
  background-color: #006b54;
  color: #ffffff;
  padding: 16px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  border: none;
  cursor: pointer;
  margin-top: 12px;
  transition: all 0.2s ease;
  box-shadow: 0 4px 6px rgba(0, 107, 84, 0.15);

  &:hover {
    background-color: #005240;
  }
`;

export default function AdminLoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const { login } = useAuth();

  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      const response = await authApi.login(email, password);

      const responseData = response.data || response;
      const { success, data, message } = responseData;

      if (success) {
        // 전역 토큰 및 세션 상태 업데이트
        login(data.accessToken, data.role, data.refreshToken);

        alert(message);

        // 사장님(ROLE_OWNER)일 경우 라우터의 루트("/") 주소로 보냅니다.
        const redirectPath =
          data.role === 'ROLE_ADMIN' ? '/admin/dashboard' : '/';

        navigate(redirectPath);
      } else {
        alert(responseData.error?.message || '로그인에 실패했습니다.');
      }
    } catch (error) {
      console.error('로그인 에러:', error);
      alert('로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.');
    }
  };

  return (
    <LoginWrapper>
      <LoginCard>
        <LoginLogo>이</LoginLogo>
        <Title>이웃 관리자</Title>
        <Subtitle>관리자 계정으로 로그인하세요.</Subtitle>

        <InputGroup>
          <label>이메일</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="admin@example.com"
            required
          />
        </InputGroup>

        <InputGroup>
          <label>비밀번호</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호를 입력하세요"
            required
          />
        </InputGroup>

        <SubmitButton onClick={handleSubmit}>로그인</SubmitButton>
      </LoginCard>
    </LoginWrapper>
  );
}
