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

export default function FindPasswordScreen() {
  const router = useRouter();

  // 1. 입력 값 및 상태
  const [email, setEmail] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');

  const [isCodeSent, setIsCodeSent] = useState(false); 
  const [isEmailVerified, setIsEmailVerified] = useState(false); 
  const [emailVerificationToken, setEmailVerificationToken] = useState(''); // 인증 완료 시 받을 토큰
  const [emailError, setEmailError] = useState('');

  // 2. 로딩 상태 관리 (중복 클릭 방지)
  const [isSending, setIsSending] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [isResetting, setIsResetting] = useState(false);

  // 이메일 유효성 검사
  const validateEmail = (text: string) => {
    setEmail(text);
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!text) setEmailError('이메일을 입력해주세요.');
    else if (!emailRegex.test(text)) setEmailError('올바른 이메일 형식이 아닙니다.');
    else setEmailError('');
  };

  // 1️[인증받기] 버튼 클릭 시 로직
  const handleSendCode = async () => {
    if (isSending) return;
    if (!email || emailError) {
      Alert.alert('알림', '올바른 이메일을 입력해주세요.');
      return;
    }

    setIsSending(true);
    try {
      const response = await client.post('/auth/password/reset-request', { email });
      
      if (response.status === 200 || response.status === 201) {
        setIsCodeSent(true);
        Alert.alert('발송 성공', `${email}로 인증 코드를 보냈습니다.`);
      }
    } catch (error: any) {
      const msg = error.response?.data?.message || error.response?.data?.error?.message || '인증 코드 발송에 실패했습니다.';
      console.error('인증코드 발송 에러:', error.response?.data || error.message);
      Alert.alert('오류', error.response?.status === 404 ? '가입되지 않은 이메일입니다.' : msg);
    } finally {
      setIsSending(false);
    }
  };

  // 2️[인증하기] 버튼 클릭 시 로직 (인증번호 확인)
  const handleVerifyCode = async () => {
    if (isVerifying) return;
    if (!verificationCode) {
      Alert.alert('알림', '인증 코드를 입력해주세요.');
      return;
    }

    setIsVerifying(true);
    try {
      const response = await client.post('/auth/email/verify', { email, code: verificationCode });
      if (response.status === 200) {
        // 인증 토큰 저장 (비밀번호 변경 시 필요할 수 있음)
        const token = response.data.emailVerificationToken || response.data.data?.emailVerificationToken || "temp_token";
        setEmailVerificationToken(token);
        setIsEmailVerified(true);
        Alert.alert('인증 성공', '이메일 인증이 완료되었습니다. 새 비밀번호를 입력해주세요.');
      }
    } catch (error: any) {
      const msg = error.response?.data?.message || '코드가 일치하지 않거나 만료되었습니다.';
      Alert.alert('인증 실패', msg);
    } finally {
      setIsVerifying(false);
    }
  };

  // 3️[변경하기] 최종 제출 로직
  const handleChangePassword = async () => {
    if (isResetting) return;
    setIsResetting(true);
    try {
      // 백엔드 스펙에 따라 emailVerificationToken이 필요할 수 있으므로 함께 보냅니다.
      const response = await client.post('/auth/password/reset', {
        email,
        newPassword,
        emailVerificationToken 
      });
      
      if (response.status === 200 || response.data?.success) {
        Alert.alert('성공', '비밀번호가 안전하게 변경되었습니다.', [
          { text: '로그인하러 가기', onPress: () => router.replace('/(auth)/login') }
        ]);
      }
    } catch (error: any) {
      const msg = error.response?.data?.message || error.response?.data?.error?.message || '비밀번호 변경 중 문제가 발생했습니다.';
      Alert.alert('오류', msg);
    } finally {
      setIsResetting(false);
    }
  };

  // 폼 유효성 검사 (로딩 중일 때도 비활성화)
  const isFormValid = isEmailVerified && newPassword.length >= 8 && newPassword === passwordConfirm && !isResetting;

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={{ flex: 1 }}>
      <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
        <SafeAreaView style={styles.container}>
          {/* 헤더 */}
          <View style={styles.header}>
            <TouchableOpacity onPress={() => router.back()}>
              <Text style={styles.backText}>{'<'}</Text>
            </TouchableOpacity>
            <Text style={styles.headerTitle}>비밀번호 찾기</Text>
            <View style={{ width: 24 }} />
          </View>

          <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
            {/* 이메일 입력 레이아웃 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>이메일</Text>
              <View style={styles.row}>
                <TextInput 
                  style={[styles.input, { flex: 1, marginRight: 10 }, emailError ? styles.inputError : null]} 
                  placeholder="이메일을 입력해주세요" 
                  value={email} 
                  onChangeText={validateEmail}
                  editable={!isEmailVerified && !isSending} // ✨ 로딩 중 입력 방지
                  autoCapitalize="none"
                  keyboardType="email-address"
                />
                <TouchableOpacity 
                  style={[styles.sideButton, (isEmailVerified || !!emailError || !email || isSending) && styles.disabledBtn]} 
                  onPress={handleSendCode}
                  disabled={isEmailVerified || !!emailError || !email || isSending}
                >
                  {isSending ? (
                    <ActivityIndicator size="small" color="#fff" />
                  ) : (
                    <Text style={styles.sideButtonText}>{isEmailVerified ? '완료' : '인증받기'}</Text>
                  )}
                </TouchableOpacity>
              </View>
              {emailError ? <Text style={styles.errorText}>{emailError}</Text> : null}
            </View>

            {/* 인증번호 입력창 */}
            <View style={styles.inputGroup}>
              <TextInput 
                style={[styles.input, !isCodeSent && styles.readOnlyInput]} 
                placeholder={isCodeSent ? "인증 코드를 입력해주세요" : "이메일 인증을 먼저 진행해주세요"} 
                value={verificationCode}
                onChangeText={setVerificationCode}
                keyboardType="default"
                editable={isCodeSent && !isEmailVerified && !isVerifying}
              />
              {isCodeSent && !isEmailVerified && (
                <TouchableOpacity 
                  style={styles.verifyLink} 
                  onPress={handleVerifyCode}
                  disabled={isVerifying}
                >
                  {isVerifying ? (
                     <ActivityIndicator size="small" color="#00A859" />
                  ) : (
                    <Text style={styles.verifyLinkText}>인증확인</Text>
                  )}
                </TouchableOpacity>
              )}
            </View>

            <View style={styles.divider} />

            {/* 새 비밀번호 입력 섹션 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>새로운 비밀번호</Text>
              <TextInput 
                style={[styles.input, !isEmailVerified && styles.readOnlyInput]} 
                placeholder="새로운 비밀번호를 입력해주세요 (8자 이상)" 
                secureTextEntry 
                value={newPassword}
                onChangeText={setNewPassword}
                editable={isEmailVerified && !isResetting}
              />
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.label}>비밀번호 확인</Text>
              <TextInput 
                style={[styles.input, !isEmailVerified && styles.readOnlyInput]} 
                placeholder="비밀번호를 한번 더 입력해주세요" 
                secureTextEntry 
                value={passwordConfirm}
                onChangeText={setPasswordConfirm}
                editable={isEmailVerified && !isResetting}
              />
            </View>

            <TouchableOpacity 
              style={[styles.submitButton, !isFormValid && styles.disabledBtn]} 
              disabled={!isFormValid}
              onPress={handleChangePassword}
            >
              {isResetting ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.submitButtonText}>변경하기</Text>
              )}
            </TouchableOpacity>
          </ScrollView>
        </SafeAreaView>
      </TouchableWithoutFeedback>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, height: 60, backgroundColor: '#00A859' },
  backText: { fontSize: 24, color: '#fff', fontWeight: 'bold' },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#fff' },
  scrollContent: { padding: 25 },
  inputGroup: { marginBottom: 20 },
  label: { fontSize: 14, fontWeight: 'bold', color: '#333', marginBottom: 8 },
  input: { borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, padding: 12, fontSize: 14, backgroundColor: '#fff' },
  inputError: { borderColor: '#FF5252' },
  readOnlyInput: { backgroundColor: '#F5F5F5', color: '#999' },
  errorText: { color: '#FF5252', fontSize: 12, marginTop: 5 },
  row: { flexDirection: 'row', alignItems: 'center' },
  sideButton: { backgroundColor: '#00A859', paddingVertical: 12, paddingHorizontal: 15, borderRadius: 4, minWidth: 80, alignItems: 'center' },
  disabledBtn: { backgroundColor: '#CCC' },
  sideButtonText: { color: '#fff', fontWeight: 'bold', fontSize: 13 },
  verifyLink: { marginTop: 8, alignSelf: 'flex-end', flexDirection: 'row', alignItems: 'center' },
  verifyLinkText: { color: '#00A859', fontWeight: 'bold', fontSize: 13 },
  divider: { height: 1, backgroundColor: '#EEE', marginVertical: 10 },
  submitButton: { backgroundColor: '#00A859', paddingVertical: 15, borderRadius: 4, alignItems: 'center', marginTop: 10 },
  submitButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' }
});