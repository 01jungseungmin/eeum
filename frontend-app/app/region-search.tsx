import React, { useState, useEffect } from 'react';
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

  // 1. 실시간 자동 검색 (디바운싱)
  useEffect(() => {
    // 검색어가 다 지워지면 밑에 뜬 결과도 비운다.
    if (!searchText.trim()) {
      setResults([]);
      return;
    }

    // 유저가 타이핑을 멈추고 0.3초(300ms)가 지나면 백엔드에 검색을 요청합니다.
    const delayDebounceFn = setTimeout(async () => {
      setIsLoading(true);
      try {
        const res = await regionApi.searchRegion(searchText);
        const data = res.data || res; 
        
        if (Array.isArray(data)) {
          setResults(data); 
        } else {
          setResults([]);
        }
      } catch (error) {
        console.error("검색 API 에러:", error);
      } finally {
        setIsLoading(false);
      }
    }, 300); 

    // 유저가 0.3초 안에 또 타자를 치면, 기존에 기다리던 타이머를 취소하고 다시 기다린다.
    return () => clearTimeout(delayDebounceFn);
  }, [searchText]); // searchText가 바뀔 때마다 이 useEffect가 실행된다

  // 2. 돋보기 버튼이나 엔터를 쳤을 때 즉시 검색
  const handleManualSearch = async () => {
    if (!searchText.trim()) return;
    
    setIsLoading(true);
    try {
      const res = await regionApi.searchRegion(searchText);
      const data = res.data || res; 
      if (Array.isArray(data)) setResults(data);
      else setResults([]);
    } catch (error) {
      console.error("수동 검색 에러:", error);
    } finally {
      setIsLoading(false);
    }
  };

  // 3. 지역 등록 함수
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

  // 4. GPS로 현재 위치 찾기 기능
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

      // [백엔드 전용 근처 지역 조회 연동 시 활성화 구간]
      try {
         const res = await regionApi.getNearbyRegions(latitude, longitude);
         if (res.success && res.data && res.data.length > 0) {
            setSearchText(res.data[0].dong);
            setResults(res.data);
            return;
         }
      } catch (err) {
         console.log("근처 조회 에러, 카카오로 대체");
      }
      

      const KAKAO_REST_API_KEY = process.env.EXPO_PUBLIC_KAKAO_REST_API_KEY;
      if (!KAKAO_REST_API_KEY) {
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
        setSearchText(regionName); // 여기서 텍스트를 바꾸면 useEffect가 자동으로 감지해서 검색해 줍니다!
      } else {
        Alert.alert('알림', '현재 위치의 정확한 동네 이름을 찾을 수 없습니다.');
      }
    } catch (error) {
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
          <TouchableOpacity onPress={handleManualSearch} style={{ padding: 4, paddingLeft: 0 }}>
            <Ionicons name="search" size={22} color="#999" />
          </TouchableOpacity>
          <TextInput 
            style={styles.searchInput} 
            placeholder="동네 이름을 검색하세요 (예: 북촌동)" 
            value={searchText}
            autoFocus={true}
            onChangeText={setSearchText}
            onSubmitEditing={handleManualSearch}
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
              onPress={() => handleAddRegion(item.regionId, item.dong)}
            >
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