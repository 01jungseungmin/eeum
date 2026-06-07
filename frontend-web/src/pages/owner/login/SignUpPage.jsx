import styled from 'styled-components';
import InputForm from '../../../components/InputForm';
import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../../api/authApi';

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

function SignUpPage() {
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [businessNumber, setBusinessNumber] = useState('');
  const [businessName, setBusinessName] = useState('');
  const [openingDate, setOpeningDate] = useState('');
  const [storeName, setStoreName] = useState('');
  const [location, setLocation] = useState('');
  const [phone, setPhone] = useState('');
  const [storePhone, setStorePhone] = useState('');
  const [token, setToken] = useState('');

  const emailRef = useRef(null);
  const codeRef = useRef(null);
  const passwordRef = useRef(null);
  const confirmPasswordRef = useRef(null);
  const businessNumberRef = useRef(null);
  const businessNameRef = useRef(null);
  const phoneRef = useRef(null);
  const openingDateRef = useRef(null);
  const storeNameRef = useRef(null);
  const locationRef = useRef(null);
  const storePhoneRef = useRef(null);

  const navigate = useNavigate();

  // 비밀번호 유효성 통과 여부 및 에러 메시지 상태 관리
  const [isPwValid, setIsPwValid] = useState(false);

  const handleEmailVerification = async (e) => {
    e.preventDefault();

    if (!email) {
      alert('이메일을 입력해주세요.');
      return;
    }

    try {
      await authApi.sendEmailVerification(email);

      alert('인증코드가 이메일로 전송되었습니다. 이메일을 확인해주세요.');
    } catch (error) {
      console.error('이메일 인증 실패:', error);
      alert('이메일 인증에 실패했습니다. 다시 시도해주세요.');
    }
  };

  const handleCodeVerification = async (e) => {
    e.preventDefault();

    if (!code) {
      alert('인증코드를 입력해주세요.');
      return;
    }

    try {
      const response = await authApi.verifyEmailCode(email, code);
      const responseData = response.data || response;

      setToken(responseData.data);
      alert('인증코드가 확인되었습니다. 회원가입을 계속 진행해주세요.');
    } catch (error) {
      console.error('인증코드 확인 실패:', error);
      alert('인증코드 확인에 실패했습니다. 다시 시도해주세요.');
    }
  };

  // 요구사항을 완벽하게 반영한 전화번호 포맷팅 함수
  const formatPhoneNumber = (value) => {
    if (!value) return value;
    const numbers = value.replace(/[^0-9]/g, '');

    // 무조건 11자리일 때
    if (numbers.length === 11) {
      return `${numbers.slice(0, 3)}-${numbers.slice(3, 7)}-${numbers.slice(7)}`;
    }

    // 서울 지역번호 '02'로 시작하는 경우 (9자리 또는 10자리)
    if (numbers.startsWith('02')) {
      if (numbers.length <= 2) return numbers;
      if (numbers.length <= 6) {
        return `${numbers.slice(0, 2)}-${numbers.slice(2)}`;
      }
      // 10자리 전까지(9자리 이하)는 무조건 02-1111-XXX 형태로 유지 (흔들림 방지)
      if (numbers.length <= 9) {
        return `${numbers.slice(0, 2)}-${numbers.slice(2, 6)}-${numbers.slice(6)}`;
      }
      // 딱 10자리일 때 -> 02-XXXX-XXXX
      return `${numbers.slice(0, 2)}-${numbers.slice(2, 6)}-${numbers.slice(6, 10)}`;
    }

    // 그 외 일반 번호
    if (numbers.length <= 3) return numbers;
    if (numbers.length <= 7) {
      return `${numbers.slice(0, 3)}-${numbers.slice(3)}`;
    }
    if (numbers.length <= 10) {
      return `${numbers.slice(0, 3)}-${numbers.slice(3, 6)}-${numbers.slice(6)}`;
    }
    return numbers;
  };

  const handlePhoneChange = (setter) => (e) => {
    const formattedValue = formatPhoneNumber(e.target.value);
    setter(formattedValue);
  };

  // 개업일자 입력에서 숫자만 허용
  const handleOpeningDateChange = (e) => {
    const onlyNumbers = e.target.value.replace(/[^0-9]/g, '');

    setOpeningDate(onlyNumbers);
  };

  const handleSignUp = async (e) => {
    e.preventDefault();

    const requiredFields = [
      { value: email, msg: '이메일', ref: emailRef },
      { value: code, msg: '인증코드', ref: codeRef },
      { value: password, msg: '비밀번호', ref: passwordRef },
      { value: confirmPassword, msg: '비밀번호 확인', ref: confirmPasswordRef },
      { value: businessNumber, msg: '사업자번호', ref: businessNumberRef },
      { value: businessName, msg: '사업자명', ref: businessNameRef },
      { value: phone, msg: '전화번호', ref: phoneRef },
      { value: openingDate, msg: '개업일자', ref: openingDateRef },
      { value: storeName, msg: '상호명', ref: storeNameRef },
      { value: location, msg: '사업장 소재지', ref: locationRef },
      {
        value: storePhone,
        msg: '사업장 전화번호',
        ref: storePhoneRef,
      },
    ];

    // 비어있는 첫 번째 필드 찾기
    const emptyField = requiredFields.find(
      (field) => !field.value || field.value.trim() === '',
    );

    if (emptyField) {
      alert(`${emptyField.msg} 항목을 입력해주세요.`);
      emptyField.ref.current?.focus();
      return;
    }

    if (!isPwValid) {
      alert(
        '비밀번호 보안 규칙을 확인해 주세요. (영문/숫자/특수문자 조합 8자 이상)',
      );
      return;
    }

    if (password !== confirmPassword) {
      alert('비밀번호가 일치하지 않습니다.');
      return;
    }

    try {
      await authApi.signUpOwner({
        email: email,
        password: password,
        name: businessName,
        phone: phone,
        businessNumber: businessNumber,
        storeName: storeName,
        openingDate: openingDate,
        storeAddress: location,
        storePhone: storePhone,
        emailVerificationToken: token,
      });

      alert('회원가입이 완료되었습니다!');
      navigate('/login');
    } catch (error) {
      console.error('회원가입 실패:', error);
      alert('회원가입에 실패했습니다. 다시 시도해주세요.');
    }
  };

  return (
    <PageWrapper>
      <Title>EEUM</Title>
      <SubTitle>사장님 가입하기</SubTitle>

      <InputForm
        ref={emailRef}
        title="이메일"
        placeholder="이메일을 입력해주세요"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        buttonText="인증받기"
        onButtonClick={handleEmailVerification}
      />
      <InputForm
        ref={codeRef}
        title="인증코드"
        placeholder="인증코드를 입력해주세요"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        buttonText="인증코드 확인"
        onButtonClick={handleCodeVerification}
      />
      <InputForm
        title="비밀번호"
        type="password"
        placeholder="영문, 숫자, 특수문자 조합 8자 이상"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        errorType="password"
        onValidate={setIsPwValid}
      />
      <InputForm
        title="비밀번호 확인"
        type="password"
        placeholder="비밀번호를 한번 더 입력해주세요"
        value={confirmPassword}
        onChange={(e) => setConfirmPassword(e.target.value)}
      />
      {confirmPassword && password !== confirmPassword && (
        <p
          style={{
            fontSize: '12px',
            color: '#ff4d4d',
            textAlign: 'left',
            marginTop: '-15px',
          }}
        >
          ⚠️ 비밀번호가 일치하지 않습니다.
        </p>
      )}
      <InputForm
        ref={businessNumberRef}
        title="사업자번호"
        placeholder="- 제외하고 입력해주세요"
        value={businessNumber}
        onChange={(e) => setBusinessNumber(e.target.value)}
      />
      <InputForm
        ref={businessNameRef}
        title="사업자명"
        placeholder="사업자명을 입력해주세요"
        value={businessName}
        onChange={(e) => setBusinessName(e.target.value)}
      />
      <InputForm
        ref={phoneRef}
        title="전화번호"
        placeholder="010-1234-5678"
        value={phone}
        onChange={handlePhoneChange(setPhone)}
        maxLength={13}
        inputMode="numeric"
      />
      <InputForm
        ref={storeNameRef}
        title="상호명"
        placeholder="상호명을 입력해주세요"
        value={storeName}
        onChange={(e) => setStoreName(e.target.value)}
      />
      <InputForm
        ref={openingDateRef}
        title="개업일자"
        placeholder="YYYYMMDD 형식으로 입력해주세요 (예: 20260605)"
        value={openingDate}
        onChange={handleOpeningDateChange}
        maxLength={8}
        inputMode="numeric"
      />
      <InputForm
        ref={locationRef}
        title="사업장 소재지"
        placeholder="사업장 소재지를 입력해주세요"
        value={location}
        onChange={(e) => setLocation(e.target.value)}
      />
      <InputForm
        ref={storePhoneRef}
        title="사업장 전화번호"
        placeholder="010-1234-5678"
        value={storePhone}
        onChange={handlePhoneChange(setStorePhone)}
        maxLength={13}
        inputMode="numeric"
      />

      <CheckboxContainer>
        <label>
          <input type="checkbox" /> [필수] 이용약관에 동의합니다.
        </label>
        <label>
          <input type="checkbox" /> [필수] 개인정보 수집 및 이용에 동의합니다.
        </label>
      </CheckboxContainer>

      <SubmitButton onClick={handleSignUp}>가입하기</SubmitButton>
    </PageWrapper>
  );
}

export default SignUpPage;
