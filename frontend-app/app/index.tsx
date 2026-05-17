import React, { useEffect, useState } from 'react';
import { View, ActivityIndicator, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import * as SecureStore from 'expo-secure-store';
import * as SplashScreen from 'expo-splash-screen';

SplashScreen.preventAutoHideAsync();

export default function IndexScreen() {
  const router = useRouter();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    // 앱이 켜지자마자 실행되는 함수
    const checkLoginState = async () => {
      try {
        // 1. 휴대폰 (SecureStore)에 'accessToken'이 있는지 확인합니다.
        const token = await SecureStore.getItemAsync('accessToken');

        if (token) {
          // 2. 토큰이 있다면? 이미 로그인한 유저이므로 홈 화면(탭)으로 보냅니다.
          router.replace('/(tabs)');
        } else {
          // 3. 토큰이 없다면? 처음 온 유저이므로 로그인 화면으로 보냅니다.
          router.replace('/(auth)/login');
        }
      } catch (error) {
        // 에러가 나면 안전하게 로그인 화면으로 보냅니다.
        console.error('로그인 상태 확인 에러:', error);
        router.replace('/(auth)/login');
      } finally {
        setIsReady(true);
      }
    };

    checkLoginState();
  }, []);

  // 라우팅 완료 후 로고 화면을 숨김
  useEffect(() => {
    if (isReady) {
      SplashScreen.hideAsync();
    }
  }, [isReady]);

  return null;
}