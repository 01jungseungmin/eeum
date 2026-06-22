import React, { useState, useEffect } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

export default function MyCommentsScreen() {
  const router = useRouter();
  const [comments, setComments] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchMyComments();
  }, []);

  const fetchMyComments = async () => {
    try {
      setIsLoading(true);
      // 백엔드 파트너가 만들어줄 '내가 쓴 댓글 API'를 호출합니다.
      const data = await communityApi.getMyComments(0, 20); 
      setComments(data);
    } catch (error) {
      console.error('내가 쓴 댓글 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const renderComment = ({ item }: { item: any }) => (
    <TouchableOpacity 
      style={styles.card} 
      // 댓글을 누르면 해당 댓글이 달려있는 게시글 원본으로 이동합니다.
      onPress={() => router.push(`/community/${item.postId || item.communityPostId}` as any)}
      activeOpacity={0.7}
    >
      {/* 상단: 원본 게시글 제목 */}
      <Text style={styles.originalPostTitle} numberOfLines={1}>
        {item.postTitle || '원본 게시글 제목'}
      </Text>
      
      {/* 중단: 내가 쓴 댓글 내용 */}
      <Text style={styles.commentContent} numberOfLines={3}>
        {item.content}
      </Text>
      
      {/* 하단: 날짜 */}
      <Text style={styles.dateText}>
        {item.createdAt ? item.createdAt.substring(0, 10) : '방금 전'}
      </Text>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>작성한 댓글</Text>
        <View style={{ width: 26 }} /> 
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : comments.length === 0 ? (
        <View style={styles.center}><Text style={styles.emptyText}>작성한 댓글이 없습니다.</Text></View>
      ) : (
        <FlatList
          data={comments}
          keyExtractor={(item, index) => item.commentId ? item.commentId.toString() : index.toString()}
          renderItem={renderComment}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FAFAFA' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backButton: { padding: 5 },
  headerTitle: { fontSize: 18, color: '#333' },
  
  listContainer: { padding: 15 },
  emptyText: { color: '#999', fontSize: 15 },

  // 피그마 카드 스타일 구현
  card: { backgroundColor: '#FFF', borderWidth: 1, borderColor: '#EAEAEA', borderRadius: 4, padding: 16, marginBottom: 12 },
  originalPostTitle: { fontSize: 13, color: '#666', marginBottom: 10 },
  commentContent: { fontSize: 15, color: '#222', marginBottom: 12, lineHeight: 22 },
  dateText: { fontSize: 12, color: '#999' }
});