import React, { useState, useRef } from 'react';
import { StyleSheet, View, ScrollView, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '../../components/CustomText';
import { WebView } from 'react-native-webview';
import * as Location from 'expo-location'; 

const categories = ['전체', '카페', '식당', '베이커리', '편의점'];

export default function MapScreen() {
  const [activeCategory, setActiveCategory] = useState('전체');
  const [isLoading, setIsLoading] = useState(false);
  const webviewRef = useRef<WebView>(null);
  
  const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY;

  const mapHtml = `
    <!DOCTYPE html>
    <html lang="ko">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <style>
        html, body {
          width: 100%;
          height: 100%;
          margin: 0;
          padding: 0;
          background-color: #F8F9FA;
        }
        #map {
          width: 100%;
          height: 100%;
        }
      </style>
    </head>

    <body>
      <div id="map">지도 로딩 중...</div>

      <script>
        // 1. React Native 앱으로 로그를 보내는 통신 함수
        function sendLog(message) {
          setTimeout(function() {
            if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
              window.ReactNativeWebView.postMessage(message);
            }
          }, 0);
        }

        sendLog('[HTML] script started');
        sendLog('[HTML] appkey=${KAKAO_JS_KEY}');
        sendLog('[HTML] href=' + window.location.href);
        sendLog('[HTML] origin=' + window.location.origin);

        // 2. 지도를 그리는 메인 함수
        function initMap() {
          sendLog('[initMap] called');
          sendLog('[initMap] typeof kakao=' + typeof kakao);

          if (typeof kakao === 'undefined') {
            sendLog('[initMap] kakao undefined');
            document.getElementById('map').innerHTML = '카카오 객체 없음';
            return;
          }

          kakao.maps.load(function() {
            sendLog('[kakao.maps.load] callback');

            try {
              var mapContainer = document.getElementById('map');
              var mapOption = {
                center: new kakao.maps.LatLng(37.548, 127.073),
                level: 3
              };

              var map = new kakao.maps.Map(mapContainer, mapOption);
              sendLog('[map] created');

              var markerPosition = new kakao.maps.LatLng(37.548, 127.073);
              var marker = new kakao.maps.Marker({
                position: markerPosition
              });

              marker.setMap(map);
              sendLog('[marker] created');

              // 3. React Native에서 위치 이동 명령을 받을 함수
              window.moveToLocation = function(lat, lng) {
                sendLog('[moveToLocation] ' + lat + ', ' + lng);
                var moveLatLon = new kakao.maps.LatLng(lat, lng);
                marker.setPosition(moveLatLon);
                map.panTo(moveLatLon);
              };

              sendLog('[moveToLocation] registered');
            } catch (e) {
              sendLog('[map error] ' + e.message);
              document.getElementById('map').innerHTML = '지도 생성 실패: ' + e.message;
            }
          });
        }

        // 4. 스크립트 동적 생성 및 순서 보장 로직
        var script = document.createElement('script');
        script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&autoload=false';

        // 스크립트 로드가 100% 완료되었을 때만 initMap 실행
        script.onload = function() {
          sendLog('[SDK] script onload');
          initMap();
        };

        // 도메인/키 문제로 스크립트 로드 실패 시
        script.onerror = function() {
          sendLog('[SDK] script onerror');
          document.getElementById('map').innerHTML = '카카오 SDK script 로드 실패';
        };

        document.head.appendChild(script);
        sendLog('[SDK] script appended');
      </script>
    </body>
    </html>
  `;

  const handleMyLocation = async () => {
    try {
      setIsLoading(true);
      const { status } = await Location.requestForegroundPermissionsAsync();
      
      if (status !== 'granted') {
        Alert.alert('권한 필요', '내 위치를 지도에 표시하려면 위치 권한 승인이 필요해요.');
        return;
      }

      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      const { latitude, longitude } = location.coords;

      const runJS = `window.moveToLocation(${latitude}, ${longitude}); true;`;
      webviewRef.current?.injectJavaScript(runJS);

    } catch (error) {
      Alert.alert('오류', '현재 위치를 불러오는 중 문제가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>이음지도</Text>
        <View style={styles.headerIcons}>
          <Ionicons name="search" size={24} color="#333" style={{ marginRight: 15 }} />
          <Ionicons name="list" size={24} color="#333" />
        </View>
      </View>

      <View style={styles.categoryContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 20 }}>
          {categories.map((cat) => (
            <TouchableOpacity 
              key={cat} 
              style={[styles.categoryBtn, activeCategory === cat && styles.categoryBtnActive]}
              onPress={() => setActiveCategory(cat)}
            >
              <Text style={[styles.categoryText, activeCategory === cat && styles.categoryTextActive]}>
                {cat}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      <View style={styles.mapArea}>
        <WebView
          ref={webviewRef}
          originWhitelist={['*']}
          source={{ html: mapHtml, baseUrl: 'https://eeum.app/' }} 
          style={{ flex: 1 }}
          javaScriptEnabled={true}
          domStorageEnabled={true}
          mixedContentMode="always"
          cacheEnabled={false}
          
        />

        <TouchableOpacity style={styles.myLocationBtn} onPress={handleMyLocation} disabled={isLoading}>
          {isLoading ? (
            <ActivityIndicator size="small" color="#333" />
          ) : (
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <Ionicons name="locate" size={16} color="#00A859" style={{ marginRight: 6 }} />
              <Text style={styles.myLocationText}>내 위치로 이동</Text>
            </View>
          )}
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15 },
  headerTitle: { fontSize: 20, fontWeight: 'bold', color: '#333' },
  headerIcons: { flexDirection: 'row' },
  categoryContainer: { paddingBottom: 15 },
  categoryBtn: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: '#F5F5F5', marginRight: 8 },
  categoryBtnActive: { backgroundColor: '#00A859' },
  categoryText: { color: '#666', fontWeight: '600' },
  categoryTextActive: { color: '#fff' },
  mapArea: { flex: 1, backgroundColor: '#F8F9FA', position: 'relative' },
  myLocationBtn: { position: 'absolute', bottom: 30, alignSelf: 'center', backgroundColor: '#fff', paddingHorizontal: 20, paddingVertical: 12, borderRadius: 25, shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 5, elevation: 5 },
  myLocationText: { fontSize: 14, fontWeight: 'bold', color: '#333' }
});