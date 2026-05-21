import React, { useState } from 'react';
import { View, StyleSheet, TextInput, TouchableOpacity, FlatList, Alert, ActivityIndicator } from 'react-native';
import { Text } from '../components/CustomText'; // 경로에 맞게 수정해주세요
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

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
  }
});