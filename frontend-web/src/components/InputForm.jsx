import styled from 'styled-components';
import { forwardRef, useState, useEffect } from 'react';

const Container = styled.div`
  width: 100%;
  margin-bottom: 20px;
`;

const LabelWrapper = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 8px;
`;

const Label = styled.label`
  font-size: 14px;
  font-weight: bold;
  text-align: left;
`;

const MessageText = styled.span`
  font-size: 12px;
  font-weight: 500;
  color: ${(props) =>
    props.$isValid ? '#00a651' : '#ff4d4d'}; // 유효성에 따라 색상 자동 변경
  margin-left: auto; // 혹시 모를 레이아웃 밀림 방지용 안전장치
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

const formatPhoneNumber = (value) => {
  if (!value) return value;

  // 숫자만 남기기
  const phone = value.replace(/[^0-9]/g, '');

  // 1. 서울 지역번호 (02)인 경우
  if (phone.startsWith('02')) {
    if (phone.length <= 2) return phone;
    if (phone.length <= 5) return `${phone.slice(0, 2)}-${phone.slice(2)}`;
    if (phone.length <= 9)
      return `${phone.slice(0, 2)}-${phone.slice(2, 5)}-${phone.slice(5)}`;
    // 최대 10자리 (02-1234-5678)
    return `${phone.slice(0, 2)}-${phone.slice(2, 6)}-${phone.slice(6, 10)}`;
  }

  // 2. 그 외 일반 번호 (010, 031, 021 등 3자리 국번 공통)
  if (phone.length <= 3) return phone;
  if (phone.length <= 6) return `${phone.slice(0, 3)}-${phone.slice(3)}`;
  if (phone.length <= 10)
    return `${phone.slice(0, 3)}-${phone.slice(3, 6)}-${phone.slice(6)}`;
  // 최대 11자리 (010-1234-5678, 021-1111-1111)
  return `${phone.slice(0, 3)}-${phone.slice(3, 7)}-${phone.slice(7, 11)}`;
};

const InputForm = forwardRef(
  (
    {
      title,
      type = 'text',
      placeholder,
      value,
      onChange,
      buttonText,
      onButtonClick,
      maxLength,
      inputMode,
      errorType,
      onValidate,
    },
    ref,
  ) => {
    const [msg, setMsg] = useState('');
    const [isValid, setIsValid] = useState(false);

    const handleInputChange = (e) => {
      if (errorType === 'phone') {
        // 실시간으로 하이픈이 포함된 포맷으로 변경
        const formatted = formatPhoneNumber(e.target.value);

        // 부모의 onChange에 하이픈이 장착된 가짜 event 객체를 전달하여 상태를 업데이트합니다.
        onChange({
          ...e,
          target: {
            ...e.target,
            value: formatted,
          },
        });
      } else {
        // 원래 일반 입력창이면 그냥 부모 onChange 실행
        onChange(e);
      }
    };

    useEffect(() => {
      if (!value) {
        setMsg('');
        setIsValid(false);
        if (onValidate) onValidate(false);
        return;
      }

      // 🔐 1. 비밀번호 가드 검사 모드
      if (errorType === 'password') {
        const passwordRegex =
          /^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%^&*()_+{}\[\]:;<>,.?~\\/-]).{8,20}$/;
        if (!passwordRegex.test(value)) {
          setMsg('영문/숫/특 조합 8자 이상');
          setIsValid(false);
          if (onValidate) onValidate(false);
        } else {
          setMsg('사용 가능한 비밀번호입니다.');
          setIsValid(true);
          if (onValidate) onValidate(true);
        }
      }

      // 🔐 2. [추가] 전화번호 가드 검사 모드
      if (errorType === 'phone' || errorType === 'storePhone') {
        // 하이픈을 걷어내고 순수 숫자 길이 검사
        const pureNumbers = value.replace(/[^0-9]/g, '');

        // 02로 시작하면 9~10자리, 그 외(010, 021 등)는 10~11자리여야 정상 번호로 인정
        const isPhoneValid =
          (pureNumbers.startsWith('02') &&
            pureNumbers.length >= 9 &&
            pureNumbers.length <= 10) ||
          (!pureNumbers.startsWith('02') &&
            pureNumbers.length >= 10 &&
            pureNumbers.length <= 11);

        if (!isPhoneValid) {
          setMsg('올바른 번호 형식이 아닙니다.');
          setIsValid(false);
          if (onValidate) onValidate(false);
        } else {
          setMsg('올바른 번호 형식입니다.');
          setIsValid(true);
          if (onValidate) onValidate(true);
        }
      }
    }, [value, errorType]);

    return (
      <Container>
        <LabelWrapper>
          <Label>{title}</Label>
          {msg && <MessageText $isValid={isValid}>{msg}</MessageText>}
        </LabelWrapper>

        <InputWrapper>
          <StyledInput
            ref={ref}
            type={type}
            placeholder={placeholder}
            value={value}
            onChange={handleInputChange} // 💡 내부 필터링 핸들러로 교체
            maxLength={maxLength}
            inputMode={inputMode}
          />
          {buttonText && (
            <ActionButton type="button" onClick={onButtonClick}>
              {buttonText}
            </ActionButton>
          )}
        </InputWrapper>
      </Container>
    );
  },
);

export default InputForm;
