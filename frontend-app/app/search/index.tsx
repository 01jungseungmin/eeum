import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ScrollView,
  Dimensions,
} from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function SearchScreen() {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState('');

  // 최근 검색어 데이터
  const [recentSearches, setRecentSearches] = useState([
    '카페 숍',
    '곱도리',
    '현대식품관',
  ]);

  // 인기 검색어 데이터 (초기값)
  const popularKeywords = [
    '카페',
    '식당',
    '곱창',
    '베이커리',
    '미용실',
    '치과',
    '마트',
    '커피',
  ];

  /*
  useEffect(() => {
    const fetchPopularKeywords = async () => {
      try {
        // 실제 서버 주소로 변경 필요 (예: client.get 또는 axios.get)
        // const response = await axios.get('/api/search/popular');
        // if (response.data) {
        //   setPopularKeywords(response.data); // 서버에서 받은 리스트로 업데이트
        // }
      } catch (error) {
        console.error("인기 검색어를 불러오는 중 오류 발생:", error);
      }
    };

    fetchPopularKeywords();
  }, []); 
  */

  const handleDeleteRecent = (keyword: string) => {
    setRecentSearches(recentSearches.filter((item) => item !== keyword));
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* 1. 상단 검색바 영역 (피그마 디자인) */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => router.back()}
          style={styles.backButton}
        >
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>

        <View style={styles.searchBarContainer}>
          <Ionicons
            name="search"
            size={20}
            color="#999"
            style={{ marginRight: 8 }}
          />
          <TextInput
            style={styles.searchInput}
            placeholder="가게 이름을 검색해보세요"
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholderTextColor="#BBB"
          />
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 2. 최근 검색어 섹션 */}
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>최근 검색어</Text>
            <TouchableOpacity onPress={() => setRecentSearches([])}>
              <Text style={styles.clearAllText}>전체삭제</Text>
            </TouchableOpacity>
          </View>

          {recentSearches.map((item, index) => (
            <View key={index} style={styles.recentItem}>
              <View style={styles.recentLeft}>
                <Ionicons name="time-outline" size={18} color="#BBB" />
                <Text style={styles.recentText}>{item}</Text>
              </View>
              <TouchableOpacity onPress={() => handleDeleteRecent(item)}>
                <Ionicons name="close" size={18} color="#BBB" />
              </TouchableOpacity>
            </View>
          ))}
        </View>

        {/* 3. 인기 검색어 섹션 (피그마 순위 강조 반영) */}
        <View style={[styles.section, { borderTopWidth: 0 }]}>
          <Text style={styles.sectionTitle}>인기 검색어</Text>
          <View style={styles.popularList}>
            {popularKeywords.map((keyword, index) => (
              <TouchableOpacity key={index} style={styles.popularItem}>
                {/* 1, 2, 3위는 초록색 강조 */}
                <Text
                  style={[
                    styles.rankText,
                    index < 3 ? styles.rankTextActive : null,
                  ]}
                >
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
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    marginRight: 12,
  },
  searchBarContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F7F7F7',
    borderRadius: 8,
    paddingHorizontal: 12,
    height: 40,
  },
  searchInput: {
    flex: 1,
    fontSize: 15,
    color: '#333',
  },
  section: {
    paddingHorizontal: 20,
    paddingTop: 24,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1E3932', // 진한 초록색 계열
  },
  clearAllText: {
    fontSize: 12,
    color: '#999',
  },
  recentItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 14,
  },
  recentLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  recentText: {
    fontSize: 15,
    color: '#444',
    marginLeft: 10,
  },
  popularList: {
    marginTop: 8,
  },
  popularItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 16,
  },
  rankText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#BBB',
    width: 30,
  },
  rankTextActive: {
    color: '#00A859', // 피그마 1,2,3위 강조색
  },
  popularKeywordText: {
    fontSize: 15,
    color: '#444',
    marginLeft: 10,
  },
});
