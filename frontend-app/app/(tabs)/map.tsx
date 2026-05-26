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
  
  // 💡 드디어 하드코딩을 지우고, .env 파일에서 안전하게 키를 불러옵니다!
  const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY;

  // ✨ 추가된 방어 로직: 키를 못 읽어왔으면 웹뷰 대신 빨간 경고창을 띄웁니다!
  if (!KAKAO_JS_KEY) {
    return (
      <SafeAreaView style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#fff' }}>
        <Text style={{ fontSize: 24, fontWeight: 'bold', color: 'red' }}>🚨 .env 키 인식 불가!</Text>
        <Text style={{ fontSize: 16, marginTop: 10, textAlign: 'center' }}>
          서버가 환경변수를 못 읽고 있습니다.{'\n'}
          터미널을 끄고 캐시를 비워서 재시작해주세요.
        </Text>
      </SafeAreaView>
    );
  }
  console.log("🔑 현재 .env에서 읽어온 카카오 JS 키: [" + KAKAO_JS_KEY + "]");

  // 🗺️ 카카오맵 최적화 HTML 
  const mapHtml = `
    <!DOCTYPE html>
    <html lang="ko">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <style>
        html, body { width: 100%; height: 100%; margin: 0; padding: 0; background-color: #F8F9FA; }
        #map { width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; }
      </style>
      <script 
        type="text/javascript" 
        src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&autoload=false"
        onload="initMap()"
      ></script>
    </head>
    <body>
      <div id="map"></div>
      
      <script>
        function initMap() {
          // 환경변수(키)가 제대로 안 들어왔을 때를 대비한 방어 로직
          if (typeof kakao === 'undefined') {
            document.getElementById('map').innerHTML = '<h3>🚨 카카오맵 로드 실패<br/>(.env 키 설정을 확인해주세요)</h3>';
            return;
          }

          kakao.maps.load(function() {
            var mapContainer = document.getElementById('map');
            var mapOption = { 
                center: new kakao.maps.LatLng(37.548, 127.073), // 초기 중심 좌표 (군자동)
                level: 3 
            };
            var map = new kakao.maps.Map(mapContainer, mapOption);

            // 초기 마커 하나 찍어두기
            var markerPosition = new kakao.maps.LatLng(37.548, 127.073); 
            var marker = new kakao.maps.Marker({ position: markerPosition });
            marker.setMap(map);

            // 앱(React Native)에서 호출하면 지도를 슥- 이동시켜줄 함수
            window.moveToLocation = function(lat, lng) {
              var moveLatLon = new kakao.maps.LatLng(lat, lng);
              marker.setPosition(moveLatLon);
              map.panTo(moveLatLon); 
            };
          });
        }
      </script>
    </body>
    </html>
  `;

  // 📍 내 위치로 부드럽게 이동하는 함수
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

      // 웹뷰 안에 있는 moveToLocation 함수를 조종(리모컨)합니다.
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
      {/* 상단 헤더 */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>이음지도</Text>
        <View style={styles.headerIcons}>
          <Ionicons name="search" size={24} color="#333" style={{ marginRight: 15 }} />
          <Ionicons name="list" size={24} color="#333" />
        </View>
      </View>

      {/* 상단 카테고리 스크롤 */}
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

      {/* 🗺️ 드디어 완성된 지도 영역! */}
      <View style={styles.mapArea}>
        <WebView
          ref={webviewRef}
          originWhitelist={['*']}
          // ✨ 성공의 일등 공신 도메인! (절대 건드리지 마세요 ㅎㅎ)
          source={{ html: mapHtml, baseUrl: 'https://eeum.app/' }} 
          style={{ flex: 1 }}
          javaScriptEnabled={true}
          domStorageEnabled={true}
          mixedContentMode="always"
        />

        {/* 내 위치 이동 버튼 */}
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