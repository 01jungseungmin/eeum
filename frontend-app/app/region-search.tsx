import React, { useState } from 'react';
import { View, StyleSheet, TextInput, TouchableOpacity, FlatList, Alert, ActivityIndicator } from 'react-native';
import { Text } from '../components/CustomText'; // 경로에 맞게 수정해주세요
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import * as Location from 'expo-location';
import axios from 'axios';

import { regionApi } from '../api/region'; 

export default function RegionSearchScreen() {
  const router = useRouter();
  const [searchText, setSearchText] = useState('');
  const [results, setResults] = useState<any[]>([]); // 검색 결과 상태
  const [isLoading, setIsLoading] = useState(false); // 로딩 상태

  // 1. 검색 실행 함수 (엔터 눌렀을 때 작동)
  const handleSearch = async () => {
    if (!searchText.trim()) {
      Alert.alert('알림', '검색어를 입력해주세요.');
      return;
    }

    setIsLoading(true);
    try {
      // API 통신 서버에서 지역 목록을 받아옵니다.
      const data = await regionApi.searchRegion(searchText);
      // 서버에서 주는 응답 형태에 맞게 세팅 (예: data.data 또는 data가 바로 배열일 수 있음)
      setResults(data.data || data); 
    } catch (error) {
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
            // ✨ API로 regionId를 서버에 던져줍니다.
            await regionApi.addRegion(regionId);
            
            Alert.alert("성공", "지역이 성공적으로 등록되었습니다.", [
              // 성공하면 이전 화면(내 지역 목록)으로 돌아갑니다.
              { text: "확인", onPress: () => router.back() }
            ]);
          } catch (e: any) {
            Alert.alert("실패", e.response?.data?.message || "지역 등록에 실패했습니다.");
          }
        }
      }
    ]);
  };

  // GPS로 현재 위치 찾기 (카카오 로컬 API + .env 적용)
  const handleCurrentLocation = async () => {
    try {
      setIsLoading(true);

      // 1. 위치 권한 묻기
      const { status } = await Location.requestForegroundPermissionsAsync();
      
      if (status !== 'granted') {
        Alert.alert('권한 필요', '현재 위치로 동네를 찾으려면 위치 권한이 필요해요.');
        return;
      }

      // 2. 현재 GPS 위도, 경도 가져오기
      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced, 
      });

      const { latitude, longitude } = location.coords;
      console.log('📍 내 위치 좌표:', latitude, longitude);

      // ✨ 3. 환경 변수(.env)에서 카카오 REST API 키 불러오기
      // (주의: .env 파일에 EXPO_PUBLIC_KAKAO_REST_API_KEY 로 저장되어 있어야 합니다!)
      const KAKAO_REST_API_KEY = process.env.EXPO_PUBLIC_KAKAO_REST_API_KEY;

      if (!KAKAO_REST_API_KEY) {
         console.error('🚨 환경 변수 에러: 카카오 API 키를 찾을 수 없습니다.');
         Alert.alert('오류', '앱 설정 문제로 위치를 찾을 수 없습니다.');
         return;
      }

      // 🚀 4. 카카오 로컬 API 호출 (좌표 -> 주소 변환)
      const response = await axios.get(
        `https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=${longitude}&y=${latitude}`,
        {
          headers: {
            Authorization: `KakaoAK ${KAKAO_REST_API_KEY}`, 
          },
        }
      );

      // 🎯 5. 응답 데이터에서 '동' 이름(행정동) 뽑아내기
      const documents = response.data.documents;
      const regionName = documents.find((doc: any) => doc.region_type === 'H')?.region_3depth_name;

      if (regionName) {
        console.log('📍 카카오가 찾아준 동네 이름:', regionName);
        
        // 검색창 텍스트를 내 동네로 업데이트
        setSearchText(regionName);
        
        // 🔗 6. 이음 서버의 동네 검색 API 호출 (동네 목록 띄우기)
        const data = await regionApi.searchRegion(regionName);
        setResults(data.data || data);

      } else {
        Alert.alert('알림', '현재 위치의 정확한 동네 이름을 찾을 수 없습니다.');
      }

    } catch (error) {
      console.error('카카오 주소 변환 에러:', error);
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
          <Ionicons name="search" size={20} color="#999" />
          <TextInput 
            style={styles.searchInput} 
            placeholder="동네 이름을 검색하세요 (예: 잠실동)" 
            value={searchText}
            autoFocus={true}
            onChangeText={setSearchText}
            // 키보드에서 '완료/검색'을 눌렀을 때 API를 호출합니다.
            onSubmitEditing={handleSearch}
            returnKeyType="search"
          />
        </View>
      </View>

      {/* 3. 현재 위치로 찾기 버튼 추가 (디자인 시안처럼 검색바 바로 아래에 배치) */}
      <TouchableOpacity style={styles.currentLocationBtn} onPress={handleCurrentLocation}>
        <Ionicons name="locate" size={18} color="#00A859" />
        <Text style={styles.currentLocationText}>현재 위치로 찾기</Text>
      </TouchableOpacity>

      {/* 로딩 중일 때 표시 */}
      {isLoading ? (
        <ActivityIndicator size="large" color="#4CAF50" style={{ marginTop: 50 }} />
      ) : (
        <FlatList 
          data={results}
          // 서버에서 오는 regionId를 키값으로 사용합니다.
          keyExtractor={(item) => item.regionId.toString()}
          showsVerticalScrollIndicator={false}
          renderItem={({ item }) => (
            <TouchableOpacity 
              style={styles.item}
              onPress={() => handleAddRegion(item.regionId, item.name)}
            >
              <Text style={styles.itemText}>{item.name}</Text>
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
  container: { 
    flex: 1, 
    backgroundColor: '#fff' 
  },
  header: { 
    flexDirection: 'row', 
    alignItems: 'center', 
    paddingHorizontal: 20, 
    paddingVertical: 10 
  },
  searchBox: { 
    flex: 1, 
    flexDirection: 'row', 
    alignItems: 'center', 
    backgroundColor: '#F5F5F5', 
    borderRadius: 8, 
    paddingHorizontal: 10, 
    height: 40,
    marginLeft: 10 // 뒤로가기 버튼과의 간격
  },
  searchInput: { 
    flex: 1, 
    marginLeft: 8, 
    fontSize: 14 
  },
  
  item: { 
    paddingVertical: 18, 
    paddingHorizontal: 20, 
    borderBottomWidth: 1, 
    borderBottomColor: '#F2F2F2' 
  },
  itemText: { 
    fontSize: 15, 
    color: '#333' 
  },

  emptyContainer: { 
    alignItems: 'center', 
    marginTop: 50 
  },
  emptyText: { 
    color: '#999', 
    fontSize: 15 
  },
  currentLocationBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    marginHorizontal: 20,
    marginTop: 10,
    backgroundColor: '#E8F5E9',
    borderRadius: 8,
  },
  currentLocationText: {
    marginLeft: 6,
    color: '#00A859',
    fontSize: 15,
    fontWeight: 'bold',
  },
});