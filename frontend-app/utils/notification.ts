// utils/notification.ts
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import { Platform } from 'react-native';
import { notificationApi } from '../api/notification';

export async function registerForPushNotificationsAsync() {
  let token;

  // 1. 에뮬레이터가 아닌 실제 기기인지 확인 (푸시 알림은 실제 기기에서만 테스트 가능)
  if (!Device.isDevice) {
    console.log('알림 테스트는 실제 디바이스 기기에서 진행해야 합니다.');
    return null;
  }

  // 2. 알림 권한 상태 확인 및 요청
  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;
  
  if (existingStatus !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }

  // 사용자가 권한을 거절한 경우 종료
  if (finalStatus !== 'granted') {
    console.log('푸시 알림 권한 획득 실패');
    return null;
  }

  try {
    // 3. Firebase 기반의 FCM 디바이스 토큰 추출
    // projectId는 expo 설정(app.json)의 주인을 따릅니다.
    token = (await Notifications.getDevicePushTokenAsync()).data;
    console.log('🔥 발급된 FCM 디바이스 토큰:', token);

    // 4. 안드로이드 기기일 경우 알림 채널(Channel) 필수 설정
    if (Platform.OS === 'android') {
      Notifications.setNotificationChannelAsync('default', {
        name: 'default',
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#00A859',
      });
    }

    // 5. 발급 완료된 진짜 토큰을 백엔드 서버로 전송하여 등록!
    if (token) {
      await notificationApi.updateFcmToken(token);
      console.log('✅ 백엔드 서버에 FCM 토큰 등록 완료');
    }

  } catch (error) {
    console.error('FCM 토큰 발급 또는 서버 등록 중 에러 발생:', error);
  }

  return token;
}