// 환경변수 불러오기
require('dotenv').config();

module.exports = {
  expo: {
    name: "EEUM",
    slug: "eeum",
    version: "1.0.0",
    orientation: "portrait",
    icon: "./assets/images/icon.png",
    
    scheme: "eeum",
    userInterfaceStyle: "automatic",

    ios: {
      supportsTablet: true,
      bundleIdentifier: "com.eeum.app",
      googleServicesFile: "./GoogleService-Info.plist",
      infoPlist: {
        LSApplicationQueriesSchemes: [
          // 소셜 로그인
          "kakaokompassauth",
          "kakaolink",
          "naversearchapp",
          "naversearchthirdlogin",
          // 결제 앱 전환. 여기 없는 스킴은 iOS가 canOpenURL을 막아
          // "앱이 설치되어 있지 않다"로 잘못 처리된다.
          "kakaotalk",
          "supertoss",
          "payco",
          "lpayapp",
          "ispmobile",
          "kftc-bankpay",
          "citispay",
          "shinhan-sr-ansimclick",
          "kb-acp",
          "mpocket.online.ansimclick",
          "hdcardappcardansimclick",
          "nhallonepayansimclick",
          "cloudpay",
          "hanawalletmembers",
          "lottesmartpay"
        ]
      }
    },
    
    android: {
      adaptiveIcon: {
        backgroundColor: "#E6F4FE",
        foregroundImage: "./assets/images/android-icon-foreground.png"
      },
      "softwareKeyboardLayoutMode": "resize",
      // android/app/build.gradle 의 applicationId 와 반드시 같아야 한다.
      // 카카오·네이버 콘솔에 등록된 패키지명도 이 값이다.
      package: "com.eeum",
      googleServicesFile: "./google-services.json"
    },
    
    plugins: [
      "expo-router",
      [
        "expo-splash-screen",
        {
          image: "./assets/images/splash-icon.png",
          imageWidth: 240,
          resizeMode: "contain",
          backgroundColor: "#4CAF50"
        }
      ],
      [
        "@react-native-seoul/kakao-login",
        {
          kakaoAppKey: process.env.EXPO_PUBLIC_KAKAO_APP_KEY,
          kotlinVersion: "2.1.0"
        }
      ],
      [
        "expo-notifications",
        {
          // 푸시 알림 상단 바에 뜰 아이콘 (배경이 투명한 흰색 로고 이미지를 사용하는 것이 정석입니다)
          icon: "./assets/images/icon.png", 
          // 알림 아이콘의 배경색 (이음의 메인 컬러로 맞춤)
          color: "#00A859" 
        }
      ]
    ],    
    experiments: {
      typedRoutes: true
    }
  }
};