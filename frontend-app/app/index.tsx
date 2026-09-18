import React, { useEffect, useState } from 'react';
import { useRouter } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';

import { getAccessToken } from '../utils/secureStore';
import { isDemoLoginEnabled, loginWithDemoAccount } from '../utils/demoAccount';
import { registerForPushNotificationsAsync } from '../utils/notification';

SplashScreen.preventAutoHideAsync();

export default function IndexScreen() {
  const router = useRouter();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    // 앱이 켜지자마자 실행되는 함수
    const checkLoginState = async () => {
      try {
        // SecureStore를 직접 부르지 않고 utils/secureStore를 거친다 —
        // 웹 빌드에서는 secureStore.web.ts(localStorage)가 잡혀야 한다.
        const token = await getAccessToken();

        if (token) {
          registerForPushNotificationsAsync();
          // 토큰이 있다면? 이미 로그인한 유저이므로 홈 화면(탭)으로 보냅니다.
          router.replace('/(tabs)');
          return;
        }

        // 웹 데모 빌드에서는 심사자가 로그인 화면에서 막히지 않도록 데모 계정으로
        // 바로 들여보낸다. 실패하면 아래 일반 로그인 화면으로 떨어진다.
        if (isDemoLoginEnabled && (await loginWithDemoAccount())) {
          router.replace('/(tabs)');
          return;
        }

        // 토큰이 없다면? 처음 온 유저이므로 로그인 화면으로 보냅니다.
        router.replace('/(auth)/login');
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
