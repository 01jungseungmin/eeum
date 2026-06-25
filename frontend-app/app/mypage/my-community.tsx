import React, { useState, useCallback } from 'react';
import { 
  View, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator, Image 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

type TabType = 'POSTS' | 'COMMENTS';

export default function MyCommunityScreen() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<TabType>('POSTS');
  const [dataList, setDataList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // 화면에 포커스 되거나 탭이 변경될 때마다 최신 데이터 로딩
  useFocusEffect(
    useCallback(() => {
      fetchMyCommunityData();
    }, [activeTab])
  );

  const fetchMyCommunityData = async () => {
    try {
      setIsLoading(true);
      let data = [];
      if (activeTab === 'POSTS') {
        data = await communityApi.getMyPosts();
      } else {
        data = await communityApi.getMyComments();
      }
      setDataList(data);
    } catch (error) {
      console.error('커뮤니티 활동 내역 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 내가 쓴 글 렌더링
  const renderPostItem = ({ item }: { item: any }) => (
    <TouchableOpacity 
      style={styles.card} 
      activeOpacity={0.7}
      onPress={() => router.push(`/community/${item.postId}` as any)}
    >
      <View style={styles.postContentSection}>
        <View style={styles.postMeta}>
          <Text style={styles.categoryBadge}>{item.categoryName || '동네소식'}</Text>
          <Text style={styles.timeText}>
            {item.createdAt ? item.createdAt.replace('T', ' ').substring(0, 16) : ''}
          </Text>
        </View>
        <Text fontWeight="bold" style={styles.postTitle} numberOfLines={2}>{item.title}</Text>
        
        <View style={styles.statsRow}>
          <Ionicons name="heart-outline" size={14} color="#888" />
          <Text style={styles.statText}>{item.likeCount || 0}</Text>
          <Ionicons name="chatbubble-outline" size={14} color="#888" />
          <Text style={styles.statText}>{item.commentCount || 0}</Text>
          <Ionicons name="eye-outline" size={14} color="#888" />
          <Text style={styles.statText}>{item.viewCount || 0}</Text>
        </View>
      </View>
      
      {item.thumbnailUrl && (
        <Image source={{ uri: item.thumbnailUrl }} style={styles.thumbnail} />
      )}
    </TouchableOpacity>
  );

  // 내가 쓴 댓글 렌더링
  const renderCommentItem = ({ item }: { item: any }) => (
    <TouchableOpacity 
      style={styles.card} 
      activeOpacity={0.7}
      // 댓글을 누르면 해당 댓글이 달린 원본 게시글로 이동합니다.
      onPress={() => router.push(`/community/${item.postId}` as any)}
    >
      <View style={{ flex: 1 }}>
        <View style={styles.commentHeader}>
          <Text style={styles.timeText}>
            {item.createdAt ? item.createdAt.replace('T', ' ').substring(0, 16) : ''}
          </Text>
          {item.reply && (
            <View style={styles.replyBadge}>
              <Text style={styles.replyBadgeText}>대댓글</Text>
            </View>
          )}
        </View>
        <Text style={styles.commentContent} numberOfLines={3}>{item.content}</Text>
        
        <View style={styles.statsRow}>
          <Ionicons name="heart" size={14} color={item.likedByMe ? "#E25555" : "#CCC"} />
          <Text style={[styles.statText, item.likedByMe && { color: "#E25555" }]}>
            {item.likeCount || 0}
          </Text>
        </View>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>동네생활 활동</Text>
        <View style={{ width: 26 }} />
      </View>

      {/* 탭 메뉴 */}
      <View style={styles.tabContainer}>
        <TouchableOpacity 
          style={[styles.tabButton, activeTab === 'POSTS' && styles.activeTabButton]}
          onPress={() => setActiveTab('POSTS')}
        >
          <Text fontWeight={activeTab === 'POSTS' ? "bold" : "normal"} 
                style={[styles.tabText, activeTab === 'POSTS' && styles.activeTabText]}>
            작성한 글
          </Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.tabButton, activeTab === 'COMMENTS' && styles.activeTabButton]}
          onPress={() => setActiveTab('COMMENTS')}
        >
          <Text fontWeight={activeTab === 'COMMENTS' ? "bold" : "normal"} 
                style={[styles.tabText, activeTab === 'COMMENTS' && styles.activeTabText]}>
            작성한 댓글
          </Text>
        </TouchableOpacity>
      </View>

      {/* 리스트 영역 */}
      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : dataList.length === 0 ? (
        <View style={styles.center}>
          <Ionicons name="document-text-outline" size={50} color="#DDD" style={{ marginBottom: 15 }} />
          <Text style={styles.emptyText}>
            {activeTab === 'POSTS' ? '작성한 게시글이 없습니다.' : '작성한 댓글이 없습니다.'}
          </Text>
        </View>
      ) : (
        <FlatList
          data={dataList}
          keyExtractor={(item, index) => 
            activeTab === 'POSTS' 
              ? `post-${item.postId || index}` 
              : `comment-${item.commentId || index}`
          }
          renderItem={activeTab === 'POSTS' ? renderPostItem : renderCommentItem}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyText: { color: '#999', fontSize: 15 },
  
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#FFF', paddingHorizontal: 15, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backButton: { padding: 4 },
  headerTitle: { fontSize: 18, color: '#333' },

  tabContainer: { flexDirection: 'row', backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#EEE' },
  tabButton: { flex: 1, paddingVertical: 14, alignItems: 'center' },
  activeTabButton: { borderBottomWidth: 2, borderBottomColor: '#1B854A' },
  tabText: { fontSize: 15, color: '#888' },
  activeTabText: { color: '#1B854A' },

  listContainer: { padding: 15 },
  
  card: { flexDirection: 'row', backgroundColor: '#FFF', borderRadius: 8, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: '#EAEAEA', shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.03, shadowRadius: 2, elevation: 1 },
  
  // 게시글 스타일
  postContentSection: { flex: 1, paddingRight: 15, justifyContent: 'space-between' },
  postMeta: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  categoryBadge: { fontSize: 11, color: '#666', backgroundColor: '#F5F5F5', paddingHorizontal: 6, paddingVertical: 3, borderRadius: 4, marginRight: 8, overflow: 'hidden' },
  timeText: { fontSize: 12, color: '#999' },
  postTitle: { fontSize: 16, color: '#333', marginBottom: 12, lineHeight: 22 },
  thumbnail: { width: 70, height: 70, borderRadius: 8, backgroundColor: '#F0F0F0', borderWidth: 1, borderColor: '#EEE' },
  
  // 댓글 스타일
  commentHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  replyBadge: { backgroundColor: '#E8F5E9', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginLeft: 8 },
  replyBadgeText: { fontSize: 11, color: '#1B854A' },
  commentContent: { fontSize: 15, color: '#444', lineHeight: 22, marginBottom: 12 },
  
  // 공통 통계 로우
  statsRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  statText: { fontSize: 13, color: '#888', marginRight: 10 },
});