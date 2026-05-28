import React, { useState } from 'react';
import { View, StyleSheet, TextInput, TouchableOpacity, FlatList, Alert, ActivityIndicator } from 'react-native';
import { Text } from '../components/CustomText'; 
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import * as Location from 'expo-location';
import axios from 'axios';

import { regionApi } from '../api/region'; 

export default function RegionSearchScreen() {
  const router = useRouter();
  const [searchText, setSearchText] = useState('');
  const [results, setResults] = useState<any[]>([]); 
  const [isLoading, setIsLoading] = useState(false); 

  // 1. 검색 실행 함수
  const handleSearch = async () => {
    if (!searchText.trim()) {
      Alert.alert('알림', '검색어를 입력해주세요.');
      return;
    }

    setIsLoading(true);
    try {
      const res = await regionApi.searchRegion(searchText);
      
      // 🔥 [디버깅] 서버가 도대체 뭐라고 답변했는지 터미널에 찍어봅니다!
      console.log("🚀 검색 API 응답 결과:", res); 

      // 💡 [방어막] 데이터가 { data: [...] } 로 오든, [...] 배열로 바로 오든 모두 커버!
      const data = res.data || res; 

      if (Array.isArray(data)) {
        setResults(data); 
      } else {
        setResults([]);
      }
    } catch (error) {
      console.error("검색 API 에러:", error);
      Alert.alert('검색 실패', '지역을 검색하는 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  // 2. 지역 등록 함수
  const handleAddRegion = async (regionId: number, name: string) => {
    Alert.alert("동네 등록", `'${name}'을(를) 활동 지역으로 등록할까요?`, [
      { text: "취소", style: "cancel" },
      { 
        text: "등록", 
        onPress: async () => {
          try {
            await regionApi.addRegion(regionId);
            
            Alert.alert("성공", "지역이 성공적으로 등록되었습니다.", [
              { text: "확인", onPress: () => router.back() }
            ]);
          } catch (e: any) {
            Alert.alert("실패", e.response?.data?.message || "지역 등록에 실패했습니다.");
          }
        }
      }
    ]);
  };

  // 3. GPS로 현재 위치 찾기 기능
  const handleCurrentLocation = async () => {
    try {
      setIsLoading(true);

      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert('권한 필요', '현재 위치로 동네를 찾으려면 위치 권한이 필요해요.');
        return;
      }

      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced, 
      });
      const { latitude, longitude } = location.coords;

      /* 🚧 [백엔드 전용 근처 지역 조회 연동 시 활성화 구간]
      // 파트너분이 만들어주신 /regions/nearby API를 사용해 자체 서버 데이터로 바로 매핑하고 싶다면 아래 주석을 켜세요!
      try {
         const res = await regionApi.getNearbyRegions(latitude, longitude);
         if (res.success && res.data && res.data.length > 0) {
            // 가장 가까운 동네 이름을 첫 번째 기준으로 검색창에 채우고 결과를 리스트에 뿌립니다.
            setSearchText(res.data[0].dong);
            setResults(res.data);
            return;
         }
      } catch (err) {
         console.log("백엔드 기반 근처 조회 미가동 또는 에러로 기존 카카오 로컬 레이어로 대체 진행합니다.");
      }
      */

      // 4. 카카오 로컬 API 호출 (기존 카카오 레이어 백업 활성화)
      const KAKAO_REST_API_KEY = process.env.EXPO_PUBLIC_KAKAO_REST_API_KEY;
      if (!KAKAO_REST_API_KEY) {
         console.error('🚨 환경 변수 에러: 카카오 API 키를 찾을 수 없습니다.');
         Alert.alert('오류', '앱 설정 문제로 위치를 찾을 수 없습니다.');
         return;
      }

      const response = await axios.get(
        `https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=${longitude}&y=${latitude}`,
        { headers: { Authorization: `KakaoAK ${KAKAO_REST_API_KEY}` } }
      );

      const documents = response.data.documents;
      const regionName = documents.find((doc: any) => doc.region_type === 'H')?.region_3depth_name;

      if (regionName) {
        setSearchText(regionName);
        
        // 💡 우리 이음 서버 주소 규격에 맞게 키워드로 재검색하여 결과 갱신
        const res = await regionApi.searchRegion(regionName);
        if (res.success && res.data) {
          setResults(res.data);
        }
      } else {
        Alert.alert('알림', '현재 위치의 정확한 동네 이름을 찾을 수 없습니다.');
      }

    } catch (error) {
      console.error('위치 처리 에러:', error);
      Alert.alert('오류', '위치 정보를 처리하는 데 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 상단 검색바 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <View style={styles.searchBox}>
          <TouchableOpacity onPress={handleSearch} style={{ padding: 4, paddingLeft: 0 }}>
            <Ionicons name="search" size={22} color="#999" />
          </TouchableOpacity>
          <TextInput 
            style={styles.searchInput} 
            placeholder="동네 이름을 검색하세요 (예: 원종1동)" 
            value={searchText}
            autoFocus={true}
            onChangeText={setSearchText}
            onSubmitEditing={handleSearch}
            returnKeyType="search"
          />
        </View>
      </View>

      {/* 현재 위치로 찾기 버튼 */}
      <TouchableOpacity style={styles.currentLocationBtn} onPress={handleCurrentLocation}>
        <Ionicons name="locate" size={18} color="#00A859" />
        <Text style={styles.currentLocationText}>현재 위치로 찾기</Text>
      </TouchableOpacity>

      {/* 로딩 및 결과 목록 */}
      {isLoading ? (
        <ActivityIndicator size="large" color="#00A859" style={{ marginTop: 50 }} />
      ) : (
        <FlatList 
          data={results}
          keyExtractor={(item) => item.regionId.toString()}
          showsVerticalScrollIndicator={false}
          renderItem={({ item }) => (
            <TouchableOpacity 
              style={styles.item}
              // 💡 백엔드 응답 속성인 'dong' 혹은 전체 주소인 'fullName' 전달 가능
              onPress={() => handleAddRegion(item.regionId, item.dong)}
            >
              {/* 💡 피그마 시안에 맞추어 시/도 군/구가 한눈에 보이는 fullName을 표출합니다 */}
              <Text style={styles.itemText}>{item.fullName}</Text>
            </TouchableOpacity>
          )}
          ListEmptyComponent={() => (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>
                {searchText ? "검색 결과가 없습니다." : "동네 이름을 검색해 보세요."}
              </Text>
            </View>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 10 },
  searchBox: { flex: 1, flexDirection: 'row', alignItems: 'center', backgroundColor: '#F5F5F5', borderRadius: 8, paddingHorizontal: 10, height: 40, marginLeft: 10 },
  searchInput: { flex: 1, marginLeft: 8, fontSize: 14 },
  item: { paddingVertical: 18, paddingHorizontal: 20, borderBottomWidth: 1, borderBottomColor: '#F2F2F2' },
  itemText: { fontSize: 15, color: '#333' },
  emptyContainer: { alignItems: 'center', marginTop: 50 },
  emptyText: { color: '#999', fontSize: 15 },
  currentLocationBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: 12, marginHorizontal: 20, marginTop: 10, backgroundColor: '#E8F5E9', borderRadius: 8 },
  currentLocationText: { marginLeft: 6, color: '#00A859', fontSize: 15, fontWeight: 'bold' },
});