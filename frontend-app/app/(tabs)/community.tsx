import React, { useState, useCallback } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ScrollView, ActivityIndicator, Image } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

const CATEGORIES = ['전체', '자유게시판', '동네소식', '분실물', '도움요청', '공동배달'];

export default function CommunityListScreen() {
  const router = useRouter();
  const [activeCategory, setActiveCategory] = useState('전체');
  const [posts, setPosts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // 탭이 포커스될 때마다 데이터 리로드
  useFocusEffect(
    useCallback(() => {
      fetchPosts();
    }, [])
  );

  const fetchPosts = async () => {
    try {
      setIsLoading(true);
      // 일단 1페이지(0) 20개 로드. 무한 스크롤 구현 시 page 번호 증가 필요
      const data = await communityApi.getPosts(0, 20); 
      setPosts(data);
    } catch (error) {
      console.error('게시글 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const renderPost = ({ item }: { item: any }) => (
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
            <Ionicons name="heart-outline" size={14} color="#888" /><Text style={styles.statText}>{item.likeCount}</Text>
            <Ionicons name="chatbubble-outline" size={14} color="#888" /><Text style={styles.statText}>{item.commentCount}</Text>
            <Ionicons name="eye-outline" size={14} color="#888" /><Text style={styles.statText}>{item.viewCount}</Text>
          </View>
        </View>
      </View>
      
      {item.thumbnailUrl && (
        <Image source={{ uri: item.thumbnailUrl }} style={styles.thumbnail} />
      )}
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>커뮤니티</Text>
        <View style={styles.headerIcons}>
          <TouchableOpacity><Ionicons name="search" size={24} color="#333" style={{ marginRight: 15 }} /></TouchableOpacity>
          <TouchableOpacity><Ionicons name="notifications-outline" size={24} color="#333" /></TouchableOpacity>
        </View>
      </View>

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

      {isLoading ? (
        <View style={styles.centerLoading}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(item) => item.postId.toString()}
          renderItem={renderPost}
          contentContainerStyle={{ paddingBottom: 100 }} 
          showsVerticalScrollIndicator={false}
        />
      )}

      {/* 라우팅 충돌을 해결한 버튼 */}
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