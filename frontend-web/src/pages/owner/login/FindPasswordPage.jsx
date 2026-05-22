import styled from 'styled-components';
import InputForm from '../../../components/InputForm';
import { useState } from 'react';
import axios from 'axios';

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
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleEmailVerification = async (e) => {
    e.preventDefault();

    try {
      const response = await axios.post(
        'http://localhost:8080/auth/password/reset-request',
        { email: email },
      );
      console.log('Verification code sent:', response.data);
      alert('인증코드가 이메일로 발송되었습니다. 이메일을 확인해주세요.');
    } catch (error) {
      console.error('Error occurred while changing password:', error);
      alert('인증코드 발송 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  };

  const handleCodeVerification = async (e) => {
    e.preventDefault();

    try {
      const response = await axios.post(
        'http://localhost:8080/auth/email/verify-code',
        { email: email, code: code },
      );
      console.log('Code verified successfully:', response.data);
      alert('인증코드가 확인되었습니다. 새로운 비밀번호를 입력해주세요.');
    } catch (error) {
      console.error('Error occurred while verifying code:', error);
      alert('인증코드 확인 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();

    if (newPassword !== confirmPassword) {
      alert('새로운 비밀번호가 일치하지 않습니다.');
      return;
    }

    try {
      const response = await axios.post(
        'http://localhost:8080/auth/password/reset',
        {
          // email: email,
          // code: code,
          // newPassword: newPassword
        },
      );
      console.log('Password changed successfully:', response.data);
      alert('비밀번호가 성공적으로 변경되었습니다.');
    } catch (error) {
      console.error('Error occurred while changing password:', error);
      alert('비밀번호 변경 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  };

  return (
    <PageWrapper>
      <Title>EEUM</Title>
      <SubTitle>비밀번호 찾기</SubTitle>

      <InputForm
        title="이메일"
        placeholder="이메일을 입력해주세요"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        buttonText="인증받기"
        onButtonClick={handleEmailVerification}
      />
      <InputForm
        title="인증코드"
        placeholder="인증코드를 입력해주세요"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        buttonText="인증코드 확인"
        onButtonClick={handleCodeVerification}
      />
      <InputForm
        title="새로운 비밀번호"
        placeholder="비밀번호를 입력해주세요"
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
        type="password"
      />
      <InputForm
        title="새로운 비밀번호 확인"
        placeholder="비밀번호를 다시 입력해주세요"
        value={confirmPassword}
        onChange={(e) => setConfirmPassword(e.target.value)}
        type="password"
      />

      <ChangeButton onClick={handleChangePassword}>변경하기</ChangeButton>
    </PageWrapper>
  );
}

export default FindPassword;
