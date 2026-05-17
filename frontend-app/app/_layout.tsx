import {
  DarkTheme,
  DefaultTheme,
  ThemeProvider,
} from '@react-navigation/native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';
import { useFonts } from 'expo-font';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useEffect } from 'react';
import * as SplashScreen from 'expo-splash-screen';

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const colorScheme = useColorScheme();

// 2. 폰트 파일 불러오기
  const [fontsLoaded, fontError] = useFonts({
    'Pretendard-Regular': require('../assets/fonts/Pretendard-Regular.otf'),
    'Pretendard-Bold': require('../assets/fonts/Pretendard-Bold.otf'),
  });

  // 3. 폰트 로딩이 끝나면 그제야 숨겨두었던 스플래시 화면을 수동으로 꺼줍니다.
  useEffect(() => {
    if (fontsLoaded || fontError) {
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError]);

  // 폰트 로딩 전에는 아무것도 렌더링하지 않고 스플래시 화면만 보여줍니다.
  if (!fontsLoaded && !fontError) {
    return null;
  }

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <Stack screenOptions={{ headerShown: false }}>
        {/* 1. 최상위 index (토큰 체크 및 리다이렉트 담당) */}
        <Stack.Screen name="index" />

        {/* 2. 인증 그룹 폴더 자체를 등록 (login, signup 등은 이 안에서 관리됨) */}
        <Stack.Screen name="(auth)" />

        {/* 3. 메인 탭 그룹 */}
        <Stack.Screen name="(tabs)" />
        
        {/* 상세 페이지 (상황에 따라 추가 가능) */}
        <Stack.Screen name="product/[id]" />
        <Stack.Screen name="shop/[id]" />
        <Stack.Screen name="search/index" />
      </Stack>
      <StatusBar style="auto" />
    </ThemeProvider>
  );
}
