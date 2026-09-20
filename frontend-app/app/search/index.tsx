import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { shopApi } from '../../api/shop';
import { usedApi } from '../../api/used';
import { PopularKeyword, searchApi } from '../../api/search';
import { StoreThumbnail } from '../../components/StoreThumbnail';

// 스토리지 키 값 상수로 정의
const RECENT_SEARCH_KEY = '@recent_searches';

type SearchScope = 'STORE' | 'USED';

type SearchResult = {
  id: number;
  title: string;
  subtitle: string;
  thumbnailUrl?: string | null;
};

export default function SearchScreen() {
  const router = useRouter();

  // 홈의 어느 탭에서 들어왔는지에 따라 상점을 찾을지 중고 글을 찾을지가 갈린다.
  const { scope: scopeParam, regionId } = useLocalSearchParams();
  const scope: SearchScope = scopeParam === 'USED' ? 'USED' : 'STORE';
  const isStore = scope === 'STORE';

  const [searchQuery, setSearchQuery] = useState('');

  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [popularKeywords, setPopularKeywords] = useState<PopularKeyword[]>([]);

  // 검색을 한 번이라도 실행했는지. 처음 들어왔을 때 "결과 없음"이 뜨면 안 된다.
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // 1. 화면이 켜질 때 최근 검색어(기기)와 인기 검색어(API)를 불러옵니다.
  useEffect(() => {
    loadRecentSearches();
    fetchPopularKeywords();
  }, [scope]);

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

  // [인기 검색어] 최근 24시간 집계를 서버에서 받아온다.
  const fetchPopularKeywords = async () => {
    const keywords = await searchApi.getPopularKeywords(scope, 10);
    setPopularKeywords(keywords);
  };

  const handleSearch = useCallback(
    async (keyword: string) => {
      const trimmed = keyword.trim();
      if (!trimmed) return;

      setSearchQuery(trimmed);
      setSubmittedKeyword(trimmed);
      setIsSearching(true);
      saveRecentSearch(trimmed);

      try {
        const region = regionId ? Number(regionId) : undefined;

        if (isStore) {
          const res = await shopApi.getShops({ keyword: trimmed, regionId: region, size: 30 });
          const content = res?.data?.content ?? res?.content ?? [];
          setResults(
            content.map((shop: any) => ({
              id: shop.storeId,
              title: shop.name,
              subtitle: shop.categoryName || shop.address || '',
              thumbnailUrl: shop.thumbnailUrl,
            }))
          );
        } else {
          const slice = await usedApi.getUsedProducts({ keyword: trimmed, regionId: region, size: 30 });
          setResults(
            slice.content.map((item) => ({
              id: item.usedProductId,
              title: item.title,
              subtitle:
                item.priceType === 'FIXED'
                  ? `${Number(item.price ?? 0).toLocaleString()}원`
                  : item.priceType === 'FREE'
                    ? '무료 나눔'
                    : '가격 협의',
              thumbnailUrl: item.thumbnailUrl,
            }))
          );
        }
      } catch (error) {
        console.error('검색 실패:', error);
        setResults([]);
      } finally {
        setIsSearching(false);
      }
    },
    [isStore, regionId, recentSearches]
  );

  const handleOpenResult = (id: number) => {
    router.push((isStore ? `/shop/${id}` : `/used-trade/${id}`) as any);
  };

  // 검색어를 지우면 다시 최근/인기 검색어 화면으로 돌아간다.
  const handleChangeQuery = (text: string) => {
    setSearchQuery(text);
    if (!text.trim()) {
      setSubmittedKeyword('');
      setResults([]);
    }
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

  const hasSubmitted = submittedKeyword.length > 0;

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
            placeholder={isStore ? '가게 이름을 검색해보세요' : '중고 물품을 검색해보세요'}
            value={searchQuery}
            onChangeText={handleChangeQuery}
            placeholderTextColor="#BBB"
            autoFocus={true}
            returnKeyType="search" // 키보드의 엔터 버튼을 '검색' 돋보기 모양으로 변경
            onSubmitEditing={() => handleSearch(searchQuery)} // 키보드 엔터 쳤을 때 검색 실행
          />
          {searchQuery.length > 0 && (
            <TouchableOpacity onPress={() => handleChangeQuery('')} hitSlop={8}>
              <Ionicons name="close-circle" size={18} color="#CCC" />
            </TouchableOpacity>
          )}
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
        {isSearching && (
          <View style={styles.centerBox}>
            <ActivityIndicator size="large" color="#00A859" />
          </View>
        )}

        {/* 검색 결과 */}
        {!isSearching && hasSubmitted && (
          results.length === 0 ? (
            <View style={styles.centerBox}>
              <Ionicons name="search-outline" size={40} color="#DDD" style={{ marginBottom: 12 }} />
              <Text style={styles.emptyText}>‘{submittedKeyword}’ 검색 결과가 없어요.</Text>
            </View>
          ) : (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>
                검색 결과 {results.length}건
              </Text>
              {results.map((item) => (
                <TouchableOpacity
                  key={item.id}
                  style={styles.resultItem}
                  onPress={() => handleOpenResult(item.id)}
                >
                  <StoreThumbnail uri={item.thumbnailUrl} style={styles.resultImage} />
                  <View style={styles.resultInfo}>
                    <Text fontWeight="bold" style={styles.resultTitle} numberOfLines={1}>
                      {item.title}
                    </Text>
                    {!!item.subtitle && (
                      <Text style={styles.resultSubtitle} numberOfLines={1}>{item.subtitle}</Text>
                    )}
                  </View>
                  <Ionicons name="chevron-forward" size={18} color="#CCC" />
                </TouchableOpacity>
              ))}
            </View>
          )
        )}

        {/* 검색 전에만 최근/인기 검색어를 보여준다 */}
        {!hasSubmitted && !isSearching && (
          <>
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
                    onPress={() => handleSearch(item)}
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

            {/* 인기 검색어 섹션. 최근 24시간 집계라 아무도 검색하지 않았으면 비어 있다 —
                그때는 빈 제목만 남지 않게 섹션째로 감춘다. */}
            {popularKeywords.length > 0 && (
              <View style={[styles.section, { borderTopWidth: 0 }]}>
                <Text style={styles.sectionTitle}>인기 검색어</Text>
                <View style={styles.popularList}>
                  {popularKeywords.map((item) => (
                    <TouchableOpacity
                      key={item.keyword}
                      style={styles.popularItem}
                      onPress={() => handleSearch(item.keyword)}
                    >
                      <Text style={[styles.rankText, item.rank <= 3 ? styles.rankTextActive : null]}>
                        {item.rank}
                      </Text>
                      <Text style={styles.popularKeywordText}>{item.keyword}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            )}
          </>
        )}
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
  centerBox: { alignItems: 'center', justifyContent: 'center', paddingVertical: 60 },
  emptyText: { fontSize: 15, color: '#999' },
  resultItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12 },
  resultImage: { width: 52, height: 52, borderRadius: 8, marginRight: 14 },
  resultInfo: { flex: 1 },
  resultTitle: { fontSize: 15, color: '#333' },
  resultSubtitle: { fontSize: 13, color: '#888', marginTop: 4 },
});
