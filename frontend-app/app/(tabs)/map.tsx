import React, { useState } from 'react';
import { StyleSheet, View, ScrollView, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '../../components/CustomText';
// 💡 웹뷰 import 추가!
import { WebView } from 'react-native-webview';

const categories = ['전체', '카페', '식당', '베이커리', '편의점'];

export default function MapScreen() {
  const [activeCategory, setActiveCategory] = useState('전체');
  
  // 💡 환경 변수에서 JavaScript 키 가져오기
  const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY;

  // 💡 웹뷰 안에 들어갈 HTML & 카카오맵 JS 코드
  // 아까 찾으신 '군자동' 좌표를 초기 중심값으로 설정해 두었습니다!
  const mapHtml = `
    <!DOCTYPE html>
    <html lang="ko">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}"></script>
      <style>
        body, html { margin: 0; padding: 0; width: 100%; height: 100%; }
        #map { width: 100%; height: 100%; }
      </style>
    </head>
    <body>
      <div id="map"></div>
      <script>
        // 1. 지도 생성
        var mapContainer = document.getElementById('map');
        var mapOption = { 
            center: new kakao.maps.LatLng(37.548, 127.073), // 군자동 좌표
            level: 3 // 확대 레벨
        };
        var map = new kakao.maps.Map(mapContainer, mapOption);

        // 2. 중앙에 마커 하나 찍어보기 (테스트용)
        var markerPosition  = new kakao.maps.LatLng(37.548, 127.073); 
        var marker = new kakao.maps.Marker({
            position: markerPosition
        });
        marker.setMap(map);
      </script>
    </body>
    </html>
  `;

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

      {/* 카테고리 스크롤 */}
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

      {/* ✨ 지도 영역 (WebView로 교체) */}
      <View style={styles.mapArea}>
        <WebView
          originWhitelist={['*']}
          source={{ html: mapHtml, baseUrl: 'http://localhost:8081' }}
          style={{ flex: 1 }}
          javaScriptEnabled={true}
        />

        {/* 내 위치로 이동 버튼 (지도 위에 둥둥 떠있게) */}
        <TouchableOpacity style={styles.myLocationBtn}>
          <Text style={styles.myLocationText}>내 위치로 이동</Text>
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
  
  // 가짜 마커 스타일은 지웠습니다!
  
  myLocationBtn: { position: 'absolute', bottom: 30, alignSelf: 'center', backgroundColor: '#fff', paddingHorizontal: 20, paddingVertical: 12, borderRadius: 25, shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 5, elevation: 5 },
  myLocationText: { fontSize: 14, fontWeight: 'bold', color: '#333' }
});