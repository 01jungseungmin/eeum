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
      package: "com.eeum.app"
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
      ]
    ],
    experiments: {
      typedRoutes: true
    }
  }
};