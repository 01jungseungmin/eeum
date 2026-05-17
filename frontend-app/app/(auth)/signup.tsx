import React, { useState } from 'react';
import { 
  StyleSheet, View, TextInput, TouchableOpacity, 
  SafeAreaView, ScrollView, Alert, 
  KeyboardAvoidingView, Platform, TouchableWithoutFeedback, Keyboard,
  ActivityIndicator
} from 'react-native';
import { useRouter } from 'expo-router';
import { client } from '../../api/client';
import { Text } from '../../components/CustomText';

export default function SignupScreen() {
  const router = useRouter();

  // 1. 입력 값 상태
  const [email, setEmail] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [phone, setPhone] = useState('');
  const [termsAgreed, setTermsAgreed] = useState(false);

  // 2. 로직, 에러 및 로딩 상태
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [isCodeSent, setIsCodeSent] = useState(false);
  const [emailVerificationToken, setEmailVerificationToken] = useState(''); // 인증 완료 시 받을 토큰
  
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [passwordConfirmError, setPasswordConfirmError] = useState('');
  
  const [isSending, setIsSending] = useState(false); // 메일 발송 로딩
  const [isVerifying, setIsVerifying] = useState(false); // 인증 확인 로딩
  const [isSigningUp, setIsSigningUp] = useState(false); // 회원가입 로딩

  // 3. 이메일 유효성 검사
  const validateEmail = (text: string) => {
    setEmail(text);
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!text) setEmailError('이메일을 입력해주세요.');
    else if (!emailRegex.test(text)) setEmailError('올바른 이메일 형식이 아닙니다.');
    else setEmailError('');
  };

  // 3-1. 전화번호 자동 하이픈 변환
  const handlePhoneChange = (text: string) => {
    const cleaned = text.replace(/[^0-9]/g, '');
    let formatted = cleaned;
    
    if (cleaned.length <= 3) {
      formatted = cleaned;
    } else if (cleaned.length <= 7) {
      formatted = `${cleaned.slice(0, 3)}-${cleaned.slice(3)}`;
    } else if (cleaned.length <= 11) {
      formatted = `${cleaned.slice(0, 3)}-${cleaned.slice(3, 7)}-${cleaned.slice(7)}`;
    } else {
      formatted = `${cleaned.slice(0, 3)}-${cleaned.slice(3, 7)}-${cleaned.slice(7, 11)}`;
    }
    setPhone(formatted);
  };

  // 4. 인증 코드 발송
  const handleSendCode = async () => {
    if (isSending) return;
    if (!email || emailError) {
      Alert.alert('알림', '올바른 이메일을 입력해주세요.');
      return;
    }
    
    setIsSending(true);
    try {
      await client.post('/auth/email/send-verification', { email });
      setIsCodeSent(true);
      Alert.alert('인증 요청', `${email}로 인증 코드를 발송했습니다.`);
    } catch (error: any) {
      console.error('발송 에러:', error.response?.data || error.message);
      Alert.alert('오류', error.response?.data?.message || '인증 코드 발송에 실패했습니다.');
    } finally {
      setIsSending(false);
    }
  };

  // 5. 인증 코드 확인
  const handleVerifyCode = async () => {
    if (isVerifying) return;
    setIsVerifying(true);
    try {
      const response = await client.post('/auth/email/verify', { 
        email, 
        code: verificationCode 
      });

      console.log(" [인증 확인 응답]:", response.data);
      
      if (response.status === 200) {
        // 서버가 돌려주는 인증 토큰을 저장
        const token = response.data.data;

        setEmailVerificationToken(token);
        setIsEmailVerified(true);
        Alert.alert('인증 성공', '이메일 인증이 완료되었습니다.');
      }
    } catch (error: any) {
      Alert.alert('인증 실패', error.response?.data?.message || '코드가 일치하지 않거나 만료되었습니다.');
    } finally {
      setIsVerifying(false);
    }
  };

  // 6. 회원가입 제출
  const handleSignup = async () => {
    if (isSigningUp) return;
    setIsSigningUp(true);
    try {
      const signupData = { 
        email, 
        password, 
        name, 
        nickname, 
        phone, 
        emailVerificationToken
      };
      
      const response = await client.post('/auth/signup', signupData);
      
      if (response.data?.success || response.status === 200 || response.status === 201) {
        Alert.alert('성공', '회원가입이 완료되었습니다!', [
          { text: '확인', onPress: () => router.replace('/(auth)/login') }
        ]);
      } else {
        Alert.alert('가입 실패', response.data?.message || '정보를 다시 확인해주세요.');
      }
    } catch (error: any) {
      const serverError = error.response?.data?.error?.message || error.response?.data?.message || '회원가입 처리 중 문제가 발생했습니다.';
      Alert.alert('오류', serverError);
    } finally {
      setIsSigningUp(false);
    }
  };

  // 7. 버튼 활성화 조건 (로딩 중일 때도 비활성화)
  const isFormValid = 
    isEmailVerified && 
    password.length >= 8 && 
    password === passwordConfirm &&
    name.length > 0 &&
    nickname.length > 0 &&
    phone.length > 11 && // 번호가 하이픈 포함 12~13자리여야 함
    termsAgreed &&
    !emailError && !passwordError && !passwordConfirmError &&
    !isSigningUp;

  return (
    <KeyboardAvoidingView 
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'} 
      style={{ flex: 1 }}
    >
      <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
        <SafeAreaView style={styles.container}>
          {/* 헤더 */}
          <View style={styles.header}>
            <TouchableOpacity onPress={() => router.back()}>
              <Text style={styles.backText}>{'<'}</Text>
            </TouchableOpacity>
            <Text style={styles.headerTitle}>회원가입</Text>
            <View style={{ width: 24 }} />
          </View>

          {/* 본문 */}
          <ScrollView 
            showsVerticalScrollIndicator={false} 
            contentContainerStyle={styles.scrollContent}
          >
            {/* 이메일 입력 섹션 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>이메일</Text>
              <View style={styles.row}>
                <TextInput 
                  style={[styles.input, { flex: 1, marginRight: 10 }, emailError ? styles.inputError : null]} 
                  placeholder="example@email.com" 
                  value={email} 
                  onChangeText={validateEmail}
                  editable={!isEmailVerified && !isSending}
                  autoCapitalize="none"
                />
                <TouchableOpacity 
                  style={[styles.sideButton, (emailError || !email || isEmailVerified || isSending) && styles.disabledSideButton]} 
                  onPress={handleSendCode}
                  disabled={!!emailError || !email || isEmailVerified || isSending}
                >
                  {isSending ? (
                    <ActivityIndicator size="small" color="#fff" />
                  ) : (
                    <Text style={styles.sideButtonText}>
                      {isEmailVerified ? '인증됨' : isCodeSent ? '재발송' : '인증받기'}
                    </Text>
                  )}
                </TouchableOpacity>
              </View>
              {emailError ? <Text style={styles.errorText}>{emailError}</Text> : null}
            </View>

            {/* 인증 코드 입력 섹션 */}
            {(isCodeSent && !isEmailVerified) && (
              <View style={styles.inputGroup}>
                <Text style={styles.label}>인증 코드</Text>
                <View style={styles.row}>
                  <TextInput 
                    style={[styles.input, { flex: 1, marginRight: 10 }]} 
                    placeholder="인증 코드 입력" 
                    value={verificationCode} 
                    onChangeText={setVerificationCode}
                    keyboardType="default" // 백엔드 설정에 따라 문자가 올 수도 있으므로
                    editable={!isVerifying}
                  />
                  <TouchableOpacity 
                    style={[styles.sideButton, (!verificationCode || isVerifying) && styles.disabledSideButton]} 
                    onPress={handleVerifyCode}
                    disabled={!verificationCode || isVerifying}
                  >
                    {isVerifying ? (
                      <ActivityIndicator size="small" color="#fff" />
                    ) : (
                      <Text style={styles.sideButtonText}>확인</Text>
                    )}
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {/* 비밀번호 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>비밀번호</Text>
              <TextInput 
                style={[styles.input, passwordError ? styles.inputError : null]} 
                placeholder="8자 이상 입력" 
                secureTextEntry 
                value={password} 
                onChangeText={(t) => {
                  setPassword(t);
                  if(t.length < 8) setPasswordError('8자 이상 입력해주세요.');
                  else setPasswordError('');
                }} 
              />
              {passwordError ? <Text style={styles.errorText}>{passwordError}</Text> : null}
            </View>

            {/* 비밀번호 확인 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>비밀번호 확인</Text>
              <TextInput 
                style={[styles.input, passwordConfirmError ? styles.inputError : null]} 
                placeholder="비밀번호 재입력" 
                secureTextEntry 
                value={passwordConfirm} 
                onChangeText={(t) => {
                  setPasswordConfirm(t);
                  if(t !== password) setPasswordConfirmError('비밀번호가 일치하지 않습니다.');
                  else setPasswordConfirmError('');
                }} 
              />
              {passwordConfirmError ? <Text style={styles.errorText}>{passwordConfirmError}</Text> : null}
            </View>

            {/* 기타 정보 입력 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>이름</Text>
              <TextInput style={styles.input} placeholder="실명을 입력해주세요" value={name} onChangeText={setName} />
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.label}>닉네임</Text>
              <TextInput style={styles.input} placeholder="닉네임을 입력해주세요" value={nickname} onChangeText={setNickname} />
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.label}>전화번호</Text>
              {/* 새로 만든 handlePhoneChange 적용 */}
              <TextInput 
                style={styles.input} 
                placeholder="숫자만 입력해주세요 (예: 01012345678)" 
                value={phone} 
                onChangeText={handlePhoneChange} 
                keyboardType="numeric" 
                maxLength={13} 
              />
            </View>

            {/* 약관 동의 */}
            <TouchableOpacity style={styles.termsRow} onPress={() => setTermsAgreed(!termsAgreed)}>
              <View style={[styles.checkbox, termsAgreed && styles.checkboxActive]}>
                {termsAgreed && <Text style={{color:'#fff'}}>✓</Text>}
              </View>
              <Text style={styles.termsText}>[필수] 이용약관 및 개인정보 처리방침 동의</Text>
            </TouchableOpacity>
          </ScrollView>

          {/* 하단 고정 버튼 */}
          <View style={styles.bottomContainer}>
            <TouchableOpacity 
              style={[styles.submitButton, !isFormValid && styles.disabledBtn]} 
              disabled={!isFormValid}
              onPress={handleSignup}
            >
              {isSigningUp ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.submitButtonText}>회원가입 완료</Text>
              )}
            </TouchableOpacity>
          </View>
        </SafeAreaView>
      </TouchableWithoutFeedback>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 15 },
  backText: { fontSize: 24, color: '#333' },
  headerTitle: { fontSize: 18, fontWeight: 'bold' },
  scrollContent: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 40 },
  inputGroup: { marginBottom: 15 },
  label: { fontSize: 14, fontWeight: '600', color: '#333', marginBottom: 8 },
  input: { borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, padding: 12, fontSize: 14 },
  inputError: { borderColor: '#FF5252' },
  errorText: { color: '#FF5252', fontSize: 12, marginTop: 5, marginLeft: 5 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sideButton: { backgroundColor: '#00A859', paddingHorizontal: 15, borderRadius: 8, height: 48, alignItems: 'center', justifyContent: 'center', minWidth: 80 },
  disabledSideButton: { backgroundColor: '#ccc' },
  sideButtonText: { color: '#fff', fontWeight: 'bold', fontSize: 13 },
  termsRow: { flexDirection: 'row', alignItems: 'center', marginVertical: 15 },
  checkbox: { width: 22, height: 22, borderWidth: 1, borderColor: '#ccc', borderRadius: 4, marginRight: 10, justifyContent: 'center', alignItems: 'center' },
  checkboxActive: { backgroundColor: '#00A859', borderColor: '#00A859' },
  termsText: { fontSize: 14, color: '#333' },
  bottomContainer: { paddingHorizontal: 20, paddingBottom: 20 },
  submitButton: { backgroundColor: '#00A859', paddingVertical: 15, borderRadius: 8, alignItems: 'center' },
  disabledBtn: { backgroundColor: '#ccc' },
  submitButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' }
});