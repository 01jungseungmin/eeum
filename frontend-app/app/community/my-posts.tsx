import React, { useState, useEffect } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

export default function MyPostsScreen() {
  const router = useRouter();
  const [posts, setPosts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchMyPosts();
  }, []);

  const fetchMyPosts = async () => {
    try {
      setIsLoading(true);
      // 백엔드 파트너가 만들어줄 '내가 쓴 글 API'를 호출합니다.
      const data = await communityApi.getMyPosts(0, 20); 
      setPosts(data);
    } catch (error) {
      console.error('내가 쓴 글 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const renderPost = ({ item }: { item: any }) => (
    <TouchableOpacity 
      style={styles.card} 
      onPress={() => router.push(`/community/${item.postId}` as any)}
      activeOpacity={0.7}
    >
      {/* 상단: 카테고리 뱃지와 날짜 */}
      <View style={styles.cardHeader}>
        <Text style={styles.categoryBadge}>{item.categoryName || '동네소식'}</Text>
        <Text style={styles.dateText}>
          {item.createdAt ? item.createdAt.substring(0, 10) : '방금 전'}
        </Text>
      </View>
      
      {/* 중단: 게시글 제목 */}
      <Text fontWeight="bold" style={styles.postTitle} numberOfLines={1}>
        {item.title}
      </Text>
      
      {/* 하단: 댓글 수 및 좋아요 수 */}
      <View style={styles.cardFooter}>
        <View style={styles.iconRow}>
          <Ionicons name="chatbubble-outline" size={16} color="#888" />
          <Text style={styles.iconText}>{item.commentCount || 0}</Text>
        </View>
        <View style={styles.iconRow}>
          <Ionicons name="heart-outline" size={16} color="#888" />
          <Text style={styles.iconText}>{item.likeCount || 0}</Text>
        </View>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>작성한 게시글</Text>
        <View style={{ width: 26 }} /> {/* 타이틀 중앙 정렬용 투명 박스 */}
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : posts.length === 0 ? (
        <View style={styles.center}><Text style={styles.emptyText}>작성한 게시글이 없습니다.</Text></View>
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(item, index) => item.postId ? item.postId.toString() : index.toString()}
          renderItem={renderPost}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FAFAFA' }, // 피그마 배경처럼 아주 연한 회색 배경
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backButton: { padding: 5 },
  headerTitle: { fontSize: 18, color: '#333' },
  
  listContainer: { padding: 15 },
  emptyText: { color: '#999', fontSize: 15 },

  // 피그마 카드 스타일 구현
  card: { backgroundColor: '#FFF', borderWidth: 1, borderColor: '#EAEAEA', borderRadius: 4, padding: 16, marginBottom: 12 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  categoryBadge: { backgroundColor: '#F5F6F8', color: '#666', fontSize: 12, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 4, overflow: 'hidden' },
  dateText: { fontSize: 12, color: '#999' },
  postTitle: { fontSize: 16, color: '#333', marginBottom: 16 },
  
  cardFooter: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  iconRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  iconText: { fontSize: 13, color: '#666' }
});