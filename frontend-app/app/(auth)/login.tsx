import React, { useState } from 'react';
import { 
  StyleSheet,  
  View, 
  TextInput, 
  TouchableOpacity, 
  SafeAreaView,
  Alert,
  KeyboardAvoidingView,
  Platform,
  TouchableWithoutFeedback,
  Keyboard,
  ActivityIndicator
} from 'react-native';
import { useRouter } from 'expo-router';
import { client } from '../../api/client'; 
import { Text } from '../../components/CustomText';
import { saveTokens } from '../../utils/secureStore';
import * as KakaoLogin from '@react-native-seoul/kakao-login';
import NaverLogin from '@react-native-seoul/naver-login';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  
  const router = useRouter();

  // 이메일 유효성 검사
  const validateEmail = (text: string) => {
    setEmail(text);
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!text) {
      setEmailError('이메일을 입력해주세요.');
    } else if (!emailRegex.test(text)) {
      setEmailError('올바른 이메일 형식이 아닙니다.');
    } else {
      setEmailError('');
    }
  };

  const isLoginValid = email.length > 0 && password.length > 0 && !emailError && !isLoading;

  const handleLogin = async () => {
    if (isLoading) return; 
    
    setIsLoading(true); 
    try {
      const response = await client.post('/auth/login', {
        email: email,
        password: password
      });

      if (response.status === 200) {
        // 두 토큰을 추출
        const accessToken = response.data.data?.accessToken || response.data.accessToken;
        const refreshToken = response.data.data?.refreshToken || response.data.refreshToken;

        // 두 토큰이 모두 잘 들어왔는지 확인
        if (accessToken && refreshToken) {
          // 안전하게 저장
          await saveTokens(accessToken, refreshToken);
          console.log('로그인 성공! Access & Refresh 토큰 저장 완료');
          router.replace('/(tabs)'); 
        } else {
          Alert.alert('오류', '로그인은 성공했지만 토큰 정보를 불러올 수 없습니다.');
        }
      }
    } catch (error: any) {
      const serverError = error.response?.data?.error?.message 
                          || error.response?.data?.message 
                          || '이메일 또는 비밀번호가 일치하지 않습니다.';
                          
      console.log('로그인 에러:', error.response?.data || error.message);
      Alert.alert('로그인 실패', serverError);
    } finally {
      setIsLoading(false); 
    }
  };

  // 카카오 로그인 핸들러
  const handleKakaoLogin = async () => {
    try {
      // 1. 카카오톡 앱을 열어서 로그인을 시도하고, 카카오 토큰을 받아옵니다.
      const result = await KakaoLogin.login();
      console.log('카카오 인증 성공! 토큰:', result.accessToken);

    const token = await KakaoLogin.login();

      // 2. 스웨거(Swagger) 명세에 맞춘 백엔드 API 호출
    const response = await client.post('/auth/login/oauth', {
      provider: 'KAKAO',
      code: token.accessToken // 발급받은 카카오 토큰을 'code' 필드에 담습니다.
    });

    // 3. 백엔드 응답 처리
    if (response.data.success) { 
      const { accessToken, refreshToken } = response.data.data;
      
      console.log('이음 서버 토큰 발급 성공!', accessToken);
      
      // 토큰 저장 로직
      await saveTokens(accessToken, refreshToken); 
      
      // 홈 화면으로 이동
      router.replace('/(tabs)');
    }

    } catch (error) {
      console.error('카카오 로그인 에러:', error);
      Alert.alert('로그인 실패', '카카오 로그인 중 오류가 발생했습니다.');
    }
  };

  // 네이버 로그인 핸들러
  const handleNaverLogin = async () => {
    try {
      // 네이버는 초기화가 필요합니다 (발급받은 ID, Secret, URL Scheme 넣기)
      NaverLogin.initialize({
        appName: 'EEUM',
        consumerKey: 'Ryfw4Zb5hvMUsAxF7N83',
        consumerSecret: 'amg0HY9SQn',
        serviceUrlSchemeIOS: 'eeum',
      });

      const { successResponse } = await NaverLogin.login();
    
    if (successResponse) {
      console.log('네이버 인증 성공! 발급된 키:', successResponse.accessToken);

      // 2. 백엔드 API 호출
      const response = await client.post('/auth/login/oauth', {
        provider: 'NAVER', // 카카오일 경우 'KAKAO'
        code: successResponse.accessToken // 발급받은 토큰을 'code' 필드에 담아 보냅니다.
      });

      // 3. 백엔드 응답 처리 (스웨거의 response 형태에 맞춤)
      if (response.data.success) {
        const { accessToken, refreshToken } = response.data.data;
        
        console.log('우리 서버 토큰 발급 성공!', accessToken);
        // 토큰 저장
        await saveTokens(accessToken, refreshToken); 
        
        router.replace('/(tabs)');
      }
    }
    } catch (error) {
      console.error('네이버 로그인 에러:', error);
    }
  };


  return (
    <KeyboardAvoidingView 
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'} 
      style={{ flex: 1 }}
    >
      <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
        <SafeAreaView style={styles.container}>
          <View style={styles.logoContainer}>
            <Text style={styles.logo}>EEUM</Text>
          </View>

          <View style={styles.formContainer}>
            <Text style={styles.label}>이메일</Text>
            <TextInput
              style={[styles.input, emailError ? styles.inputError : null]}
              placeholder="이메일을 입력해주세요"
              value={email}
              onChangeText={validateEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              editable={!isLoading}
            />
            {emailError ? <Text style={styles.errorText}>{emailError}</Text> : null}

            <Text style={styles.label}>비밀번호</Text>
            <TextInput
              style={styles.input}
              placeholder="비밀번호를 입력해주세요"
              value={password}
              onChangeText={setPassword}
              secureTextEntry 
              editable={!isLoading}
            />

            <View style={styles.optionsContainer}>
              <Text style={styles.optionText}>☐ 자동 로그인</Text>
              <TouchableOpacity onPress={() => router.push('/(auth)/find-password')}>
                <Text style={styles.optionText}>비밀번호 찾기</Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity 
              style={[styles.loginButton, !isLoginValid && styles.disabledBtn]} 
              onPress={handleLogin}
              disabled={!isLoginValid}
            >
              {isLoading ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.loginButtonText}>로그인</Text>
              )}
            </TouchableOpacity>

            <TouchableOpacity 
              style={styles.signupButton}
              onPress={() => router.push('/(auth)/signup')} 
              disabled={isLoading}
            >
              <Text style={styles.signupButtonText}>이메일로 회원가입</Text>
            </TouchableOpacity>

            <View style={styles.dividerContainer}>
              <View style={styles.line} />
              <Text style={styles.dividerText}>또는</Text>
              <View style={styles.line} />
            </View>

            <TouchableOpacity style={styles.kakaoButton} onPress={handleKakaoLogin}>
              <Text style={styles.kakaoIcon}>💬</Text> 
              <Text style={styles.kakaoButtonText}>카카오로 로그인</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.naverButton} onPress={handleNaverLogin}>
              <Text style={styles.naverIcon}>N</Text>
              <Text style={styles.naverButtonText}>네이버로 로그인</Text>
            </TouchableOpacity>
          </View>
        </SafeAreaView>
      </TouchableWithoutFeedback>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  logoContainer: { alignItems: 'center', marginTop: 60, marginBottom: 30 },
  logo: { fontSize: 40, fontWeight: '900', color: '#00A859', letterSpacing: 2 },
  formContainer: { paddingHorizontal: 30 },
  label: { fontSize: 14, fontWeight: '600', color: '#333', marginBottom: 8 },
  input: { borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, paddingHorizontal: 15, paddingVertical: 12, fontSize: 14, marginBottom: 15 },
  inputError: { borderColor: '#FF5252' },
  errorText: { color: '#FF5252', fontSize: 12, marginTop: -10, marginBottom: 10, marginLeft: 5 },
  optionsContainer: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 25 },
  optionText: { fontSize: 12, color: '#888' },
  loginButton: { backgroundColor: '#00A859', paddingVertical: 15, borderRadius: 8, alignItems: 'center', marginBottom: 15 },
  disabledBtn: { backgroundColor: '#E0E0E0' },
  loginButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  signupButton: { alignItems: 'center', marginBottom: 20 },
  signupButtonText: { color: '#888', fontSize: 14, textDecorationLine: 'underline' },
  dividerContainer: { flexDirection: 'row', alignItems: 'center', marginBottom: 20 },
  line: { flex: 1, height: 1, backgroundColor: '#E0E0E0' },
  dividerText: { marginHorizontal: 15, color: '#888', fontSize: 12 },
  kakaoButton: { backgroundColor: '#FEE500', paddingVertical: 15, borderRadius: 8, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginBottom: 12 },
  kakaoIcon: { position: 'absolute', left: 20, fontSize: 18 },
  kakaoButtonText: { color: '#000000', fontSize: 15, fontWeight: 'bold' },
  naverButton: { backgroundColor: '#03C75A', paddingVertical: 15, borderRadius: 8, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  naverIcon: { position: 'absolute', left: 20, fontSize: 18, color: '#fff', fontWeight: '900' },
  naverButtonText: { color: '#ffffff', fontSize: 15, fontWeight: 'bold' }
});