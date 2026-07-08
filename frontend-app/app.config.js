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
          "kakaokompassauth",
          "naversearchapp",
          "naversearchthirdlogin"
        ]
      }
    },
    
    android: {
      adaptiveIcon: {
        backgroundColor: "#E6F4FE",
        foregroundImage: "./assets/images/android-icon-foreground.png"
      },
      "softwareKeyboardLayoutMode": "resize",
      package: "com.eeum.app",
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