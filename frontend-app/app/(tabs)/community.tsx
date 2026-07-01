import React, { useState, useCallback, useEffect } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ScrollView, ActivityIndicator, Image, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';
import { useDebounce } from '../../hooks/useDebounce';

const CATEGORIES = ['전체', '자유게시판', '동네소식', '분실물', '도움요청', '공동배달'];

export default function CommunityListScreen() {
  const router = useRouter();
  
  const [activeCategory, setActiveCategory] = useState('전체');
  const [posts, setPosts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // 검색 관련 상태
  const [isSearchMode, setIsSearchMode] = useState(false);
  const [keyword, setKeyword] = useState('');
  
  // 타이핑 후 0.5초 대기 시 작동하는 디바운스 훅
  const debouncedKeyword = useDebounce(keyword, 500); 

  // 1. 화면에 돌아올 때마다 데이터 새로고침 (다른 화면 갔다 왔을 때)
  useFocusEffect(
    useCallback(() => {
      fetchPosts();
    }, [])
  );

  // 2. 검색어나 카테고리가 바뀔 때 즉각적으로 실행되도록 useEffect로 분리!
  useEffect(() => {
    fetchPosts();
  }, [activeCategory, debouncedKeyword]);

  const fetchPosts = async () => {
    try {
      setIsLoading(true);

      // 백엔드 API에 검색어 전달
      let data = await communityApi.getPosts(0, 50, debouncedKeyword); 

      // 카테고리 필터링 (프론트 처리)
      if (activeCategory !== '전체') {
        data = data.filter((item: any) => item.categoryName === activeCategory);
      }

      if (debouncedKeyword && debouncedKeyword.trim() !== '') {
        const lowerKeyword = debouncedKeyword.toLowerCase();
        data = data.filter((item: any) => 
          (item.title && item.title.toLowerCase().includes(lowerKeyword)) || 
          (item.content && item.content.toLowerCase().includes(lowerKeyword))
        );
      }
      
      setPosts(data);
    } catch (error) {
      console.error('게시글 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCloseSearch = () => {
    setIsSearchMode(false);
    setKeyword(''); 
  };

  const renderPost = ({ item }: { item: any }) => {
    const isLiked = item.likedByMe || false; 

    return (
      <TouchableOpacity 
        style={styles.postItem} 
        onPress={() => router.push(`/community/${item.postId}` as any)}
      >
        <View style={styles.postContentSection}>
          <View style={styles.postMeta}>
            <Text style={styles.categoryBadge}>{item.categoryName || '동네소식'}</Text>
            <Text style={styles.timeText}>
              {item.createdAt ? item.createdAt.replace('T', ' ').substring(0, 10) : '방금 전'}
            </Text>
          </View>
          <Text fontWeight="bold" style={styles.postTitle} numberOfLines={1}>{item.title}</Text>
          
          <View style={styles.postFooter}>
            <Text style={styles.authorText}>{item.authorNickname || '익명'}</Text>
            <View style={styles.statsRow}>
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
      
      {/* 헤더 영역 */}
      {isSearchMode ? (
        <View style={styles.searchHeader}>
          <TouchableOpacity onPress={handleCloseSearch} style={{ marginRight: 10 }}>
            <Ionicons name="chevron-back" size={26} color="#333" />
          </TouchableOpacity>
          <View style={styles.searchInputWrapper}>
            <Ionicons name="search" size={20} color="#999" style={styles.searchIconInside} />
            <TextInput
              style={styles.searchInput}
              placeholder="제목 또는 내용으로 검색해보세요"
              value={keyword}
              onChangeText={setKeyword}
              autoFocus 
            />
            {keyword.length > 0 && (
              <TouchableOpacity onPress={() => setKeyword('')} style={styles.clearIconInside}>
                <Ionicons name="close-circle" size={18} color="#CCC" />
              </TouchableOpacity>
            )}
          </View>
        </View>
      ) : (
        <View style={styles.header}>
          <Text fontWeight="bold" style={styles.headerTitle}>동네생활</Text>
          <View style={styles.headerIcons}>
            <TouchableOpacity onPress={() => setIsSearchMode(true)}>
              <Ionicons name="search" size={24} color="#333" />
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* 카테고리 가로 스크롤 탭 */}
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

      {/* 게시글 리스트 */}
      {isLoading ? (
        <View style={styles.centerLoading}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : posts.length === 0 ? (
        <View style={styles.centerLoading}>
          <Ionicons name="search-outline" size={40} color="#CCC" style={{ marginBottom: 10 }} />
          <Text style={{ color: '#999', fontSize: 15 }}>등록된 게시글이 없습니다.</Text>
        </View>
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(item) => item.postId?.toString()}
          renderItem={renderPost}
          contentContainerStyle={{ paddingBottom: 100 }} 
          showsVerticalScrollIndicator={false}
        />
      )}

      {/* 글쓰기 FAB 버튼 */}
      <TouchableOpacity style={styles.fab} onPress={() => router.push('/community/write' as any)}>
        <Ionicons name="pencil" size={24} color="#FFF" />
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  centerLoading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15 },
  headerTitle: { fontSize: 22, color: '#333' },
  headerIcons: { flexDirection: 'row' },
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