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
import { Platform } from 'react-native';
import * as SplashScreen from 'expo-splash-screen';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';

// 🚨 파일 경로가 다를 경우 프로젝트 구조에 맞게 수정해 주세요 (예: '@/api/notification' 또는 './api/notification')
import { notificationApi } from '../api/notification'; 

SplashScreen.preventAutoHideAsync();

// 앱이 켜져 있을 때 알림이 오면 화면에 어떻게 띄울지 설정
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,    // 앱 사용 중 상단에 팝업 배너 띄우기
    shouldShowList: true,      // 스마트폰의 알림 센터 목록에 남기기
    shouldPlaySound: true,     // 알림음 재생
    shouldSetBadge: true,      // 앱 아이콘 우측 상단 숫자 뱃지 업데이트
  }),
});

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const router = useRouter();

  // 폰트 파일 불러오기
  const [fontsLoaded, fontError] = useFonts({
    'Pretendard-Regular': require('../assets/fonts/Pretendard-Regular.otf'),
    'Pretendard-Bold': require('../assets/fonts/Pretendard-Bold.otf'),
  });

  useEffect(() => {
    // 1. 앱 켜질 때 푸시 권한 묻고 토큰을 서버로 보내는 로직
    const registerForPushNotificationsAsync = async () => {
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('default', {
          name: 'default',
          importance: Notifications.AndroidImportance.MAX,
        });
      }

      if (Device.isDevice) {
        const { status: existingStatus } = await Notifications.getPermissionsAsync();
        let finalStatus = existingStatus;
        
        if (existingStatus !== 'granted') {
          const { status } = await Notifications.requestPermissionsAsync();
          finalStatus = status;
        }
        
        if (finalStatus !== 'granted') {
          console.log('푸시 알림 권한이 거부되었습니다.');
          return;
        }
        
        try {
          // 기기 고유 토큰 발급
          const tokenData = await Notifications.getExpoPushTokenAsync();
          const fcmToken = tokenData.data;
          console.log("🔥 내 기기 푸시 토큰:", fcmToken);
          
          // 백엔드 DB에 내 토큰 저장 요청
          await notificationApi.updateFcmToken(fcmToken);
          console.log("✅ 백엔드로 푸시 토큰 전송 성공!");
        } catch (error) {
          console.log("푸시 토큰 전송 실패:", error);
        }
      } else {
        console.log('푸시 알림 토큰 발급은 실제 기기에서만 가능합니다.');
      }
    };

    // 토큰 발급 및 전송 함수 실행
    registerForPushNotificationsAsync();

    // --------------------------------------------------------

    // [리스너 1] 앱이 켜진 상태(포그라운드)에서 알림이 도착했을 때
    const notificationSubscription = Notifications.addNotificationReceivedListener(notification => {
      console.log('📱 포그라운드 알림 수신:', notification);
    });

    // [리스너 2] 유저가 스마트폰 상단 알림 배너를 클릭했을 때
    const responseSubscription = Notifications.addNotificationResponseReceivedListener(response => {
      // 백엔드가 FCM Payload의 'data' 영역에 담아 보낸 linkUrl을 추출
      const linkUrl = response.notification.request.content.data?.linkUrl;
      console.log('🚀 알림 클릭됨, 이동할 주소:', linkUrl);
      
      if (linkUrl) {
        // 해당 주소로 유저를 즉시 리다이렉트
        router.push(linkUrl as any);
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
        <Stack.Screen name="notification-settings" options={{ animation: 'slide_from_right' }} />
      </Stack>
      <StatusBar style="auto" />
    </ThemeProvider>
  );
}