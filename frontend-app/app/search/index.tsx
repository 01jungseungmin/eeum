import React, { useState, useEffect } from 'react';
import {
  View,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ScrollView,
} from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage'; // ✨ 추가됨

// 스토리지 키 값 상수로 정의
const RECENT_SEARCH_KEY = '@recent_searches';

export default function SearchScreen() {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState('');

  // 최근/인기 검색어 State (초기값은 빈 배열로 시작)
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [popularKeywords, setPopularKeywords] = useState<string[]>([]);

  // 1. 화면이 켜질 때 최근 검색어(기기)와 인기 검색어(API)를 불러옵니다.
  useEffect(() => {
    loadRecentSearches();
    fetchPopularKeywords();
  }, []);

  // [최근 검색어] 기기에서 불러오기
  const loadRecentSearches = async () => {
    try {
      const savedSearches = await AsyncStorage.getItem(RECENT_SEARCH_KEY);
      if (savedSearches) {
        setRecentSearches(JSON.parse(savedSearches));
      }
    } catch (error) {
      console.error('최근 검색어 불러오기 실패:', error);
    }
  };

  // [최근 검색어] 검색할 때마다 기기에 저장하기 (최대 10개 유지)
  const saveRecentSearch = async (keyword: string) => {
    if (!keyword.trim()) return;

    try {
      // 중복 검색어 제거 후 맨 앞에 새 검색어 추가
      const filtered = recentSearches.filter((item) => item !== keyword);
      const updated = [keyword, ...filtered].slice(0, 10); // 최대 10개까지만 유지
      
      setRecentSearches(updated);
      await AsyncStorage.setItem(RECENT_SEARCH_KEY, JSON.stringify(updated));
    } catch (error) {
      console.error('최근 검색어 저장 실패:', error);
    }
  };

  // [인기 검색어] 백엔드 API에서 불러오기
  const fetchPopularKeywords = async () => {
    try {
      // TODO: 백엔드 API가 완성되면 아래 주석을 풀고 연결하세요!
      // const response = await api.get('/search/popular');
      // setPopularKeywords(response.data); 
      
      // 임시 더미 데이터 (API가 연결되기 전까지만 사용)
      setPopularKeywords(['카페', '식당', '곱창', '베이커리', '미용실', '치과', '마트', '커피']);
    } catch (error) {
      console.error("인기 검색어를 불러오는 중 오류 발생:", error);
    }
  };

  // 검색 실행 (엔터 치거나, 검색어 리스트를 눌렀을 때 실행)
  const handleSearch = (keyword: string) => {
    if (!keyword.trim()) return;
    
    saveRecentSearch(keyword); // 검색어 저장
    
    // TODO: 검색 결과 화면으로 이동하는 로직 (파라미터로 keyword 전달)
    // router.push(`/search-result?query=${keyword}`);
    console.log(`'${keyword}' 검색 실행!`);
  };

  // 개별 검색어 삭제
  const handleDeleteRecent = async (keyword: string) => {
    const updated = recentSearches.filter((item) => item !== keyword);
    setRecentSearches(updated);
    await AsyncStorage.setItem(RECENT_SEARCH_KEY, JSON.stringify(updated));
  };

  // 검색어 전체 삭제
  const handleClearAllRecent = async () => {
    setRecentSearches([]);
    await AsyncStorage.removeItem(RECENT_SEARCH_KEY);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => router.back()}
          style={styles.backButton}
        >
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>

        <View style={styles.searchBarContainer}>
          <Ionicons name="search" size={20} color="#999" style={{ marginRight: 8 }} />
          <TextInput
            style={styles.searchInput}
            placeholder="가게 이름을 검색해보세요"
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholderTextColor="#BBB"
            autoFocus={true}
            returnKeyType="search" // 키보드의 엔터 버튼을 '검색' 돋보기 모양으로 변경
            onSubmitEditing={() => handleSearch(searchQuery)} // 키보드 엔터 쳤을 때 검색 실행
          />
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
        {/* 최근 검색어 섹션 */}
        {recentSearches.length > 0 && (
          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>최근 검색어</Text>
              <TouchableOpacity onPress={handleClearAllRecent}>
                <Text style={styles.clearAllText}>전체삭제</Text>
              </TouchableOpacity>
            </View>

            {recentSearches.map((item, index) => (
              <TouchableOpacity 
                key={index} 
                style={styles.recentItem}
                onPress={() => handleSearch(item)} // ✨ 클릭하면 해당 단어로 검색
              >
                <View style={styles.recentLeft}>
                  <Ionicons name="time-outline" size={18} color="#BBB" />
                  <Text style={styles.recentText}>{item}</Text>
                </View>
                {/* 삭제 버튼은 검색 실행되지 않도록 이벤트 전파 막기(히트박스 분리) */}
                <TouchableOpacity 
                  onPress={() => handleDeleteRecent(item)} 
                  hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                >
                  <Ionicons name="close" size={18} color="#BBB" />
                </TouchableOpacity>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* 인기 검색어 섹션 */}
        <View style={[styles.section, { borderTopWidth: 0 }]}>
          <Text style={styles.sectionTitle}>인기 검색어</Text>
          <View style={styles.popularList}>
            {popularKeywords.map((keyword, index) => (
              <TouchableOpacity 
                key={index} 
                style={styles.popularItem}
                onPress={() => handleSearch(keyword)} // ✨ 클릭하면 해당 단어로 검색
              >
                <Text style={[styles.rankText, index < 3 ? styles.rankTextActive : null]}>
                  {index + 1}
                </Text>
                <Text style={styles.popularKeywordText}>{keyword}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 12 },
  backButton: { marginRight: 12 },
  searchBarContainer: { flex: 1, flexDirection: 'row', alignItems: 'center', backgroundColor: '#F7F7F7', borderRadius: 8, paddingHorizontal: 12, height: 40 },
  searchInput: { flex: 1, fontSize: 15, color: '#333' },
  section: { paddingHorizontal: 20, paddingTop: 24 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  sectionTitle: { fontSize: 16, fontWeight: 'bold', color: '#1E3932' },
  clearAllText: { fontSize: 12, color: '#999' },
  recentItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 14 },
  recentLeft: { flexDirection: 'row', alignItems: 'center' },
  recentText: { fontSize: 15, color: '#444', marginLeft: 10 },
  popularList: { marginTop: 8 },
  popularItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 16 },
  rankText: { fontSize: 16, fontWeight: 'bold', color: '#BBB', width: 30 },
  rankTextActive: { color: '#00A859' },
  popularKeywordText: { fontSize: 15, color: '#444', marginLeft: 10 },
});