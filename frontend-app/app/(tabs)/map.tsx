import React, { useState } from 'react';
import { StyleSheet, View, ScrollView, TouchableOpacity, DimensionValue } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '../../components/CustomText';

const categories = ['전체', '카페', '식당', '베이커리', '편의점'];

export default function MapScreen() {
  const [activeCategory, setActiveCategory] = useState('전체');

  // 마커를 그리는 공통 컴포넌트
const MapMarker = ({ name, top, left }: { name: string, top: DimensionValue, left: DimensionValue }) => (
  <View style={[styles.markerWrapper, { top, left }]}>
    <Ionicons name="location-sharp" size={40} color="#00A859" style={{ marginBottom: -10 }} />
    <View style={styles.markerLabel}>
      <Text style={styles.markerText}>{name}</Text>
    </View>
  </View>
);

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

      {/* 지도 영역 (카카오맵 API 들어갈 자리) */}
      <View style={styles.mapArea}>
        {/* 가짜 마커들 (임의의 위치) */}
        <MapMarker name="골드락 카페" top="30%" left="40%" />
        <MapMarker name="한식당 맛집" top="45%" left="65%" />
        <MapMarker name="베이커리 빵집" top="55%" left="20%" />
        <MapMarker name="동네 약국" top="65%" left="35%" />

        {/* 현재 위치 마커 (중앙) */}
        <View style={[styles.markerWrapper, { top: '45%', left: '40%' }]}>
          <Ionicons name="location" size={50} color="#888" style={{ marginBottom: -15 }} />
          <Text style={styles.centerText}>지도 영역</Text>
          <Text style={styles.centerSubText}>(4개 상점)</Text>
        </View>

        {/* 내 위치로 이동 버튼 */}
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
  
  markerWrapper: { position: 'absolute', alignItems: 'center' },
  markerLabel: { backgroundColor: '#fff', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6, shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 3, elevation: 3 },
  markerText: { fontSize: 12, fontWeight: '600', color: '#333' },
  
  centerText: { fontSize: 14, color: '#888', fontWeight: 'bold', marginTop: 5 },
  centerSubText: { fontSize: 12, color: '#AAA' },

  myLocationBtn: { position: 'absolute', bottom: 30, alignSelf: 'center', backgroundColor: '#fff', paddingHorizontal: 20, paddingVertical: 12, borderRadius: 25, shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 5, elevation: 5 },
  myLocationText: { fontSize: 14, fontWeight: 'bold', color: '#333' }
});
