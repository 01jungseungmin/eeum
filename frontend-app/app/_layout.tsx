import {
  DarkTheme,
  DefaultTheme,
  ThemeProvider,
} from '@react-navigation/native';
import { Stack, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';
import { useFonts } from 'expo-font';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useEffect } from 'react';
import * as SplashScreen from 'expo-splash-screen';
import * as Notifications from 'expo-notifications';

SplashScreen.preventAutoHideAsync();

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,    // 앱 사용 중 상단에 팝업 배너를 띄울지 여부 (shouldShowAlert 대체)
    shouldShowList: true,      // 스마트폰의 알림 센터 목록에 남길지 여부
    shouldPlaySound: true,     // 알림음 재생 여부
    shouldSetBadge: true,      // 앱 아이콘 우측 상단 숫자 뱃지 업데이트 여부
  }),
});

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const router = useRouter();

// 2. 폰트 파일 불러오기
  const [fontsLoaded, fontError] = useFonts({
    'Pretendard-Regular': require('../assets/fonts/Pretendard-Regular.otf'),
    'Pretendard-Bold': require('../assets/fonts/Pretendard-Bold.otf'),
  });

  useEffect(() => {
    // [리스너 1] 앱이 켜진 상태에서 알림이 도착했을 때 실행됨
    const notificationSubscription = Notifications.addNotificationReceivedListener(notification => {
      console.log('📱 포그라운드 알림 수신:', notification);
    });

    // [리스너 2] 유저가 스마트폰 상단 알림 배너를 클릭했을 때 실행됨
    const responseSubscription = Notifications.addNotificationResponseReceivedListener(response => {
      // 백엔드가 FCM Payload의 'data' 영역에 담아 보낸 linkUrl을 추출합니다.
      const linkUrl = response.notification.request.content.data?.linkUrl;
      
      console.log('🚀 알림 클릭됨, 이동할 주소:', linkUrl);
      
      if (linkUrl) {
        // 해당 주소로 유저를 즉시 리다이렉트 시킵니다.
        router.push(linkUrl as any);
      }
    });

    // 컴포넌트가 언마운트될 때 리스너를 깨끗이 청소(해제)합니다.
    return () => {
      notificationSubscription.remove();
      responseSubscription.remove();
    };
  }, []);

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
        <Stack.Screen name="notification-settings" options={{ animation: 'slide_from_right' }} />
      </Stack>
      <StatusBar style="auto" />
    </ThemeProvider>
  );
}
