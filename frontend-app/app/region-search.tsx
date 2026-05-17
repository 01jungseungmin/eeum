import React, { useState } from 'react';
import { View, StyleSheet, TextInput, TouchableOpacity, FlatList, Alert } from 'react-native';
import { Text } from '../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
// import { regionService } from '../services/regionService'; // 나중에 백엔드 연결 시 주석 해제

const DUMMY_REGIONS = [
  { id: 1, name: "서울특별시 송파구 잠실본동" },
  { id: 2, name: "서울특별시 송파구 잠실2동" },
  { id: 3, name: "서울특별시 강남구 역삼동" },
];

export default function RegionSearchScreen() {
  const router = useRouter();
  const [searchText, setSearchText] = useState('');
  const [results, setResults] = useState(DUMMY_REGIONS);

  const handleAddRegion = async (regionId: number, name: string) => {
    Alert.alert("동네 등록", `'${name}'을(를) 활동 지역으로 등록할까요?`, [
      { text: "취소", style: "cancel" },
      { 
        text: "등록", 
        onPress: async () => {
          try {
            // const res = await regionService.addRegion(regionId);
            // if (res.success) { ... }
            
            // 임시 성공 처리
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
            onChangeText={(t) => {
              setSearchText(t);
              // 입력한 글자가 포함된 동네만 필터링
              setResults(DUMMY_REGIONS.filter(r => r.name.includes(t)));
            }}
          />
        </View>
      </View>

      {/* 동네 검색 결과 리스트 */}
      <FlatList 
        data={results}
        keyExtractor={(item) => item.id.toString()}
        showsVerticalScrollIndicator={false}
        renderItem={({ item }) => (
          <TouchableOpacity 
            style={styles.item} // ✨ 문제가 되었던 스타일입니다. 하단에 추가 완료!
            onPress={() => handleAddRegion(item.id, item.name)}
          >
            <Text style={styles.itemText}>{item.name}</Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={() => (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>검색 결과가 없습니다.</Text>
          </View>
        )}
      />
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