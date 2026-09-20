import {
  DarkTheme,
  DefaultTheme,
  ThemeProvider,
} from '@react-navigation/native';
import { Stack, useRouter } from 'expo-router';
import { Platform } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';
import { useFonts } from 'expo-font';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useEffect } from 'react';
import * as SplashScreen from 'expo-splash-screen';
import * as Notifications from 'expo-notifications';
import { getNotificationRoute } from '../utils/notificationRoute';
import { applyWebAlertPatch } from '../utils/webAlertPatch';
import { applyMobileFrame } from '../utils/mobileFrame';

// 화면이 그려지기 전에 갈아끼워야 한다. 첫 Alert.alert 호출보다 늦으면 그 건은 그냥 사라진다.
applyWebAlertPatch();

// PC 브라우저에서 폰 너비 틀로 가둔다. Dimensions를 함께 보정하므로 첫 렌더보다
// 먼저 돌아야 한다 — 늦으면 화면들이 보정 전 너비로 이미지 크기를 잡아버린다.
// 네이티브는 빈 구현이라 아무 일도 하지 않는다.
applyMobileFrame();

SplashScreen.preventAutoHideAsync();

// 푸시 알림은 네이티브 기기 전용이다. 웹 데모 빌드에서 이 설정·리스너가 돌면
// 모듈이 없어 루트 레이아웃 평가 단계에서 터지고 화면이 통째로 비어 버린다.
const isPushSupported = Platform.OS !== 'web';

// 앱이 켜져 있을 때 알림이 오면 화면에 어떻게 띄울지 설정
if (isPushSupported) {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,    // 앱 사용 중 상단에 팝업 배너 띄우기
      shouldShowList: true,      // 스마트폰의 알림 센터 목록에 남기기
      shouldPlaySound: true,     // 알림음 재생
      shouldSetBadge: true,      // 앱 아이콘 우측 상단 숫자 뱃지 업데이트
    }),
  });
}

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const router = useRouter();

  // 폰트 파일 불러오기
  const [fontsLoaded, fontError] = useFonts({
    'Pretendard-Regular': require('../assets/fonts/Pretendard-Regular.otf'),
    'Pretendard-Bold': require('../assets/fonts/Pretendard-Bold.otf'),
  });

  useEffect(() => {
    if (!isPushSupported) return;

    // 네이티브 푸시 토큰(FCM/APNs) 등록은 utils/notification.ts의
    // registerForPushNotificationsAsync가 로그인/자동로그인 시점에 전담한다.
    // 여기서 Expo Push Token을 추가로 등록하면 마지막에 저장된 토큰으로
    // 덮어써져 백엔드(FCM HTTP v1)가 발송에 사용할 수 없는 토큰이 남는다.

    // [리스너 1] 앱이 켜진 상태(포그라운드)에서 알림이 도착했을 때
    const notificationSubscription = Notifications.addNotificationReceivedListener(notification => {
      console.log('📱 포그라운드 알림 수신:', notification);
    });

    // [리스너 2] 유저가 스마트폰 상단 알림 배너를 클릭했을 때
    const responseSubscription = Notifications.addNotificationResponseReceivedListener(response => {
      // 백엔드가 FCM Payload의 'data' 영역에 담아 보낸 정보를 앱 라우트로 변환
      const data = response.notification.request.content.data;
      const route = getNotificationRoute(data);

      if (route) {
        router.push(route as any);
      } else {
        console.warn('⚠️ 알림 클릭: 이동할 화면을 찾지 못함', data);
      }
    });

    // 컴포넌트가 언마운트될 때 리스너 해제
    return () => {
      notificationSubscription.remove();
      responseSubscription.remove();
    };
  }, []);

  // 폰트 로딩이 끝나면 스플래시 화면 숨기기
  useEffect(() => {
    if (fontsLoaded || fontError) {
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) {
    return null;
  }

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="product/[id]" />
        <Stack.Screen name="shop/[id]" />
        <Stack.Screen name="search/index" />
        {/* 파일은 app/mypage/notification-setting.tsx 다 (단수, mypage 아래).
            이름이 어긋나 있던 동안 expo-router가 "No route named ..." 경고를 내고
            이 화면만 전환 애니메이션이 빠졌다. */}
        <Stack.Screen name="mypage/notification-setting" options={{ animation: 'slide_from_right' }} />
      </Stack>
      <StatusBar style="auto" />
    </ThemeProvider>
  );
}