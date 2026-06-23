import React, { useState, useCallback } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ScrollView, ActivityIndicator, Image, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';
import { useDebounce } from '../../hooks/useDebounce';

// 1. 이름과 백엔드 DB 번호를 짝지어주는 매핑 객체 생성
const CATEGORY_MAP: Record<string, number | null> = {
  '전체': null,       // 전체는 번호 없이 null 전송
  '자유게시판': 8,
  '동네소식': 9,
  '분실물': 10,
  '도움요청': 11,
  '공동배달': 12,
};

const CATEGORIES = Object.keys(CATEGORY_MAP);

export default function CommunityListScreen() {
  const router = useRouter();
  
  const [activeCategory, setActiveCategory] = useState('전체');
  const [posts, setPosts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // ✨ 검색 관련 상태 추가
  const [isSearchMode, setIsSearchMode] = useState(false); // 검색창 활성화 여부
  const [keyword, setKeyword] = useState(''); // 입력 중인 검색어
  
  // ✨ 타이핑 후 0.5초(500ms) 동안 입력이 멈추면 값이 업데이트됩니다.
  const debouncedKeyword = useDebounce(keyword, 500); 

  // 화면이 보이거나, 탭(카테고리)이 바뀌거나, 검색어(디바운스됨)가 바뀔 때 실행!
  useFocusEffect(
    useCallback(() => {
      fetchPosts();
    }, [activeCategory, debouncedKeyword]) 
  );

  const fetchPosts = async () => {
    try {
      setIsLoading(true);
      const targetCategoryId = CATEGORY_MAP[activeCategory]; 
      
      // ✨ 카테고리 아이디와 검색어를 함께 넘깁니다!
      let data = await communityApi.getPosts(0, 20, targetCategoryId, debouncedKeyword); 

      // (선택) 백엔드 버그 수정 전까지 사용하는 프론트 강제 필터링 코드 (카테고리)
      if (activeCategory !== '전체') {
        data = data.filter((item: any) => item.categoryName === activeCategory);
      }
      
      setPosts(data);
    } catch (error) {
      console.error('게시글 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 검색창 닫기 (초기화)
  const handleCloseSearch = () => {
    setIsSearchMode(false);
    setKeyword(''); // 검색어를 지우면 자동으로 전체 목록이 다시 불러와집니다.
  };

  // 🚨 에러 원인 해결: 소괄호 ( ) 가 아니라 중괄호 { } 로 열고 닫아야 내부에 변수를 선언할 수 있습니다!
  const renderPost = ({ item }: { item: any }) => {
    // 백엔드 데이터에 맞춰서 좋아요 여부를 가져옵니다.
    const isLiked = item.likedByMe || item.isLiked || false; 

    return (
      <TouchableOpacity style={styles.postItem} onPress={() => router.push(`/community/${item.postId}` as any)}>
        <View style={styles.postContentSection}>
          <View style={styles.postMeta}>
            <Text style={styles.categoryBadge}>{item.categoryName || '동네소식'}</Text>
            <Text style={styles.timeText}>
              {item.createdAt ? new Date(item.createdAt).toLocaleDateString() : '방금 전'}
            </Text>
          </View>
          <Text fontWeight="bold" style={styles.postTitle} numberOfLines={1}>{item.title}</Text>
          
          <View style={styles.postFooter}>
            <Text style={styles.authorText}>{item.authorNickname || '익명'}</Text>
            <View style={styles.statsRow}>
              
              {/* ✨ 좋아요 여부에 따라 하트 색상과 종류가 붉은색으로 바뀝니다! */}
              <Ionicons 
                name={isLiked ? "heart" : "heart-outline"} 
                size={14} 
                color={isLiked ? "#E25555" : "#888"} 
              />
              <Text style={[styles.statText, isLiked && { color: "#E25555" }]}>
                {item.likeCount || 0}
              </Text>
              
              <Ionicons name="chatbubble-outline" size={14} color="#888" />
              <Text style={styles.statText}>{item.commentCount || 0}</Text>
              
              <Ionicons name="eye-outline" size={14} color="#888" />
              <Text style={styles.statText}>{item.viewCount || 0}</Text>
            </View>
          </View>
        </View>
        
        {item.thumbnailUrl && (
          <Image source={{ uri: item.thumbnailUrl }} style={styles.thumbnail} />
        )}
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      
      {/* ✨ 헤더: 검색 모드와 일반 모드 분기 처리 */}
      {isSearchMode ? (
        <View style={styles.searchHeader}>
          <TouchableOpacity onPress={handleCloseSearch} style={{ marginRight: 10 }}>
            <Ionicons name="chevron-back" size={26} color="#333" />
          </TouchableOpacity>
          <View style={styles.searchInputWrapper}>
            <Ionicons name="search" size={20} color="#999" style={styles.searchIconInside} />
            <TextInput
              style={styles.searchInput}
              placeholder="검색어를 입력하세요"
              value={keyword}
              onChangeText={setKeyword}
              autoFocus // 돋보기 누르면 바로 키보드가 올라옴
            />
            {/* 검색어가 있을 때만 지우기(X) 버튼 표시 */}
            {keyword.length > 0 && (
              <TouchableOpacity onPress={() => setKeyword('')} style={styles.clearIconInside}>
                <Ionicons name="close-circle" size={18} color="#CCC" />
              </TouchableOpacity>
            )}
          </View>
        </View>
      ) : (
        <View style={styles.header}>
          <Text fontWeight="bold" style={styles.headerTitle}>커뮤니티</Text>
          <View style={styles.headerIcons}>
            {/* ✨ 돋보기 버튼 누르면 검색 모드 켜기 */}
            <TouchableOpacity onPress={() => setIsSearchMode(true)}>
              <Ionicons name="search" size={24} color="#333" style={{ marginRight: 15 }} />
            </TouchableOpacity>
            <TouchableOpacity><Ionicons name="notifications-outline" size={24} color="#333" /></TouchableOpacity>
          </View>
        </View>
      )}

      {/* 카테고리 (검색 모드일 때도 특정 카테고리 내에서 검색 가능하도록 유지) */}
      <View style={styles.categoryContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categoryScroll}>
          {CATEGORIES.map((cat) => (
            <TouchableOpacity 
              key={cat} 
              style={[styles.categoryChip, activeCategory === cat && styles.activeCategoryChip]}
              onPress={() => setActiveCategory(cat)}
            >
              <Text style={[styles.categoryText, activeCategory === cat && styles.activeCategoryText]}>{cat}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* 리스트 영역 */}
      {isLoading ? (
        <View style={styles.centerLoading}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : posts.length === 0 ? (
        <View style={styles.centerLoading}>
          <Ionicons name="search-outline" size={40} color="#CCC" style={{ marginBottom: 10 }} />
          <Text style={{ color: '#999', fontSize: 15 }}>검색 결과가 없습니다.</Text>
        </View>
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(item) => item.postId.toString()}
          renderItem={renderPost}
          contentContainerStyle={{ paddingBottom: 100 }} 
          showsVerticalScrollIndicator={false}
        />
      )}

      <TouchableOpacity style={styles.fab} onPress={() => router.push('/community/write' as any)}>
        <Ionicons name="pencil" size={24} color="#FFF" />
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  centerLoading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  
  // 일반 헤더
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15 },
  headerTitle: { fontSize: 22, color: '#333' },
  headerIcons: { flexDirection: 'row' },
  
  // ✨ 검색 헤더 스타일
  searchHeader: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 10 },
  searchInputWrapper: { flex: 1, flexDirection: 'row', alignItems: 'center', backgroundColor: '#F5F6F8', borderRadius: 8, paddingHorizontal: 10 },
  searchIconInside: { marginRight: 8 },
  searchInput: { flex: 1, height: 40, fontSize: 15, color: '#333' },
  clearIconInside: { padding: 4 },

  categoryContainer: { borderBottomWidth: 1, borderBottomColor: '#EEE', paddingBottom: 10 },
  categoryScroll: { paddingHorizontal: 15 },
  categoryChip: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: '#F5F6F8', marginHorizontal: 4 },
  activeCategoryChip: { backgroundColor: '#1B854A' },
  categoryText: { fontSize: 14, color: '#666', fontWeight: '600' },
  activeCategoryText: { color: '#fff' },
  
  postItem: { flexDirection: 'row', padding: 20, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  postContentSection: { flex: 1, paddingRight: 15, justifyContent: 'space-between' },
  postMeta: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  categoryBadge: { fontSize: 12, color: '#666', backgroundColor: '#F5F5F5', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginRight: 8 },
  timeText: { fontSize: 12, color: '#999' },
  postTitle: { fontSize: 16, color: '#333', marginBottom: 15 },
  postFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  authorText: { fontSize: 12, color: '#888' },
  statsRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  statText: { fontSize: 12, color: '#888', marginRight: 8 },
  thumbnail: { width: 80, height: 80, borderRadius: 8, backgroundColor: '#F0F0F0', borderWidth: 1, borderColor: '#EEE' },
  
  fab: { position: 'absolute', bottom: 20, right: 20, width: 56, height: 56, borderRadius: 28, backgroundColor: '#1B854A', justifyContent: 'center', alignItems: 'center', elevation: 5, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 3.84 }
});