import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, TextInput, KeyboardAvoidingView, Platform, ScrollView, ActivityIndicator, Alert, Image, Keyboard } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

export default function CommunityDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();
  
  const [post, setPost] = useState<any>(null);
  const [comments, setComments] = useState<any[]>([]);
  const [inputText, setInputText] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [isLiked, setIsLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);

  // ✨ 1. 대댓글(답글) 대상을 기억하는 상태 추가
  const [replyTarget, setReplyTarget] = useState<{ commentId: number; authorName: string } | null>(null);

  useEffect(() => {
    if (id) fetchDetailAndComments();
  }, [id]);

  const fetchDetailAndComments = async () => {
    try {
      setIsLoading(true);
      const [postData, commentsData] = await Promise.all([
        communityApi.getPostDetail(id as string),
        communityApi.getComments(id as string)
      ]);
      setPost(postData);
      setIsLiked(postData.likedByMe);
      setLikeCount(postData.likeCount);
      setComments(commentsData);
    } catch (error) {
      console.error('상세 정보 로딩 실패:', error);
      Alert.alert('오류', '게시글을 불러올 수 없습니다.');
      router.back();
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggleLike = async () => {
    setIsLiked(!isLiked);
    setLikeCount((prev) => isLiked ? prev - 1 : prev + 1);

    try {
      if (isLiked) {
        await communityApi.unlikePost(id as string);
      } else {
        await communityApi.likePost(id as string);
      }
    } catch (error) {
      console.error('좋아요 토글 실패', error);
      setIsLiked(isLiked); 
      setLikeCount((prev) => isLiked ? prev + 1 : prev - 1);
      Alert.alert('오류', '좋아요 처리에 실패했습니다.');
    }
  };

  const handleToggleCommentLike = async (commentId: number | string, currentIsLiked: boolean) => {
    setComments((prevComments) =>
      prevComments.map((comment) => {
        if (comment.commentId === commentId || comment.id === commentId) {
          return {
            ...comment,
            likedByMe: !currentIsLiked,
            likeCount: currentIsLiked ? (comment.likeCount || 1) - 1 : (comment.likeCount || 0) + 1,
          };
        }
        return comment;
      })
    );

    try {
      if (currentIsLiked) {
        await communityApi.unlikeComment(commentId);
      } else {
        await communityApi.likeComment(commentId);
      }
    } catch (error) {
      console.error('댓글 좋아요 실패', error);
      setComments((prevComments) =>
        prevComments.map((comment) => {
          if (comment.commentId === commentId || comment.id === commentId) {
            return {
              ...comment,
              likedByMe: currentIsLiked,
              likeCount: currentIsLiked ? (comment.likeCount || 0) + 1 : (comment.likeCount || 1) - 1,
            };
          }
          return comment;
        })
      );
      Alert.alert('오류', '좋아요 처리에 실패했습니다.');
    }
  };

  // ✨ 2. 전송 로직 분기 (일반 댓글 vs 대댓글)
  const handleSubmitComment = async () => {
    if (!inputText.trim()) return;
    
    try {
      setIsSubmitting(true);
      
      if (replyTarget) {
        // 답글 대상이 있으면 대댓글 API 호출
        await communityApi.createReply(replyTarget.commentId, inputText);
      } else {
        // 없으면 일반 댓글 API 호출
        await communityApi.createComment(id as string, inputText);
      }
      
      // 작성 성공 시 초기화 및 새로고침
      setInputText('');
      setReplyTarget(null); // 답글 모드 해제
      Keyboard.dismiss(); // 키보드 내리기
      
      const newComments = await communityApi.getComments(id as string);
      setComments(newComments);
      
    } catch (error: any) {
      const serverMessage = error.response?.data?.message;
      Alert.alert('작성 실패', serverMessage || '일시적인 오류가 발생했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading || !post) {
    return (
      <SafeAreaView style={styles.centerLoading}>
        <ActivityIndicator size="large" color="#1B854A" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 1. 상단 헤더 영역 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={28} color="#333" />
        </TouchableOpacity>
        <View style={styles.headerIcons}>
          <TouchableOpacity style={{ marginRight: 15 }}><Ionicons name="share-social-outline" size={24} color="#333" /></TouchableOpacity>
          <TouchableOpacity><Ionicons name="ellipsis-vertical" size={24} color="#333" /></TouchableOpacity>
        </View>
      </View>

      {/* 2. 키보드가 화면 전체를 밀어 올려주도록 감싸는 영역 */}
      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
          {/* 본문 영역 */}
          <View style={styles.postSection}>
            <View style={styles.authorRow}>
              <View style={styles.authorAvatar}><Ionicons name="person" size={20} color="#CCC" /></View>
              <View style={{ flex: 1 }}>
                <Text fontWeight="bold" style={styles.authorName}>{post.authorNickname || post.authorName || post.nickname || '익명'}</Text>
                <Text style={styles.postTime}>{post.createdAt ? new Date(post.createdAt).toLocaleString() : '방금 전'}</Text>
              </View>
              <Text style={styles.categoryBadge}>{post.categoryName || post.category || '동네소식'}</Text>
            </View>

            <Text fontWeight="bold" style={styles.title}>{post.title}</Text>
            
            {post.imageUrl && (
              <Image source={{ uri: post.imageUrl }} style={styles.postImage} resizeMode="cover" />
            )}

            <Text style={styles.content}>{post.content}</Text>

            <View style={styles.statsRow}>
              <TouchableOpacity onPress={handleToggleLike} style={{ flexDirection: 'row', alignItems: 'center' }}>
                <Ionicons name={isLiked ? "heart" : "heart-outline"} size={16} color={isLiked ? "#E25555" : "#888"} />
                <Text style={[styles.statText, { color: isLiked ? "#E25555" : "#888", marginLeft: 4 }]}>{likeCount}</Text>
              </TouchableOpacity>
              <View style={{ flexDirection: 'row', alignItems: 'center', marginLeft: 12 }}>
                <Ionicons name="chatbubble-outline" size={16} color="#888" />
                <Text style={[styles.statText, { marginLeft: 4 }]}>{post.commentCount || comments.length}</Text>
              </View>
            </View>
          </View>

          <View style={styles.divider} />

          {/* 댓글 및 대댓글 영역 */}
          <View style={styles.commentSection}>
            <Text fontWeight="bold" style={styles.commentHeader}>댓글 {comments.length}</Text>
            
            {comments.map((comment: any, index) => {
              const currentCommentId = comment.commentId || comment.id; 
              const authorName = comment.authorNickname || comment.authorName || comment.nickname || '익명';
              
              return (
                <View key={currentCommentId || index}>
                  {/* 메인 댓글 */}
                  <View style={styles.commentItem}>
                    <View style={styles.commentAvatar}><Ionicons name="person" size={16} color="#CCC" /></View>
                    <View style={{ flex: 1 }}>
                      <View style={styles.commentAuthorRow}>
                        <Text fontWeight="bold" style={styles.commentAuthor}>{authorName}</Text>
                        <Text style={styles.commentTime}>{comment.createdAt ? new Date(comment.createdAt).toLocaleTimeString() : '방금'}</Text>
                      </View>
                      <Text style={styles.commentText}>{comment.content}</Text>
                      
                      <View style={styles.commentActionRow}>
                        <TouchableOpacity 
                          style={styles.commentActionBtn}
                          onPress={() => handleToggleCommentLike(currentCommentId, comment.likedByMe)}
                        >
                          <Ionicons name={comment.likedByMe ? "heart" : "heart-outline"} size={12} color={comment.likedByMe ? "#E25555" : "#888"} />
                          <Text style={[styles.commentActionText, comment.likedByMe && { color: "#E25555" }]}>
                            좋아요 {comment.likeCount > 0 ? comment.likeCount : ''}
                          </Text>
                        </TouchableOpacity>
                        
                        <TouchableOpacity 
                          style={styles.commentActionBtn}
                          onPress={() => {
                            setReplyTarget({ commentId: currentCommentId, authorName: authorName });
                          }}
                        >
                          <Ionicons name="chatbubble-outline" size={12} color="#888" />
                          <Text style={styles.commentActionText}>답글 달기</Text>
                        </TouchableOpacity>
                      </View>
                    </View>
                  </View>

                  {/* 대댓글(Replies) 리스트 */}
                  {comment.replies && comment.replies.length > 0 && (
                    <View style={styles.repliesContainer}>
                      {comment.replies.map((reply: any, rIndex: number) => {
                        const replyId = reply.replyId || reply.commentId || reply.id;
                        const replyAuthor = reply.authorNickname || reply.authorName || '익명';
                        return (
                          <View key={replyId || rIndex} style={styles.replyItem}>
                            <Ionicons name="return-down-forward-outline" size={16} color="#BBB" style={styles.replyIcon} />
                            <View style={styles.commentAvatar}><Ionicons name="person" size={16} color="#CCC" /></View>
                            <View style={{ flex: 1 }}>
                              <View style={styles.commentAuthorRow}>
                                <Text fontWeight="bold" style={styles.commentAuthor}>{replyAuthor}</Text>
                                <Text style={styles.commentTime}>{reply.createdAt ? new Date(reply.createdAt).toLocaleTimeString() : '방금'}</Text>
                              </View>
                              <Text style={styles.commentText}>{reply.content}</Text>
                            </View>
                          </View>
                        );
                      })}
                    </View>
                  )}
                </View>
              );
            })}
          </View>
        </ScrollView>

        {/* 3. 하단 고정 입력창 및 답글 배너 영역 */}
        {replyTarget && (
          <View style={styles.replyBanner}>
            <Text style={styles.replyBannerText}>
              <Text fontWeight="bold">{replyTarget.authorName}</Text>님에게 답글 남기는 중...
            </Text>
            <TouchableOpacity onPress={() => setReplyTarget(null)}>
              <Ionicons name="close-circle" size={20} color="#999" />
            </TouchableOpacity>
          </View>
        )}
        
        <View style={styles.inputContainer}>
          <TextInput
            style={styles.input}
            placeholder={replyTarget ? "답글을 입력하세요" : "댓글을 입력하세요"}
            value={inputText}
            onChangeText={setInputText}
            multiline
          />
          <TouchableOpacity 
            style={[styles.submitBtn, !inputText.trim() && { opacity: 0.5 }]} 
            onPress={handleSubmitComment}
            disabled={isSubmitting || !inputText.trim()}
          >
            {isSubmitting ? <ActivityIndicator size="small" color="#fff" /> : <Text style={styles.submitBtnText}>등록</Text>}
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  centerLoading: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#fff' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backButton: { paddingRight: 10 },
  headerIcons: { flexDirection: 'row' },
  
  postSection: { padding: 20 },
  authorRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  authorAvatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#F0F0F0', justifyContent: 'center', alignItems: 'center', marginRight: 10 },
  authorName: { fontSize: 15, color: '#333' },
  postTime: { fontSize: 12, color: '#888', marginTop: 2 },
  categoryBadge: { fontSize: 12, color: '#666', backgroundColor: '#F5F5F5', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 4 },
  title: { fontSize: 20, color: '#333', marginBottom: 15, lineHeight: 28 },
  postImage: { width: '100%', height: 200, borderRadius: 8, marginBottom: 15, backgroundColor: '#F5F5F5' },
  content: { fontSize: 16, color: '#444', lineHeight: 24, marginBottom: 20 },
  statsRow: { flexDirection: 'row', alignItems: 'center' },
  statText: { fontSize: 13, color: '#888' },
  
  divider: { height: 8, backgroundColor: '#F8F9FA' },
  
  commentSection: { padding: 20, paddingBottom: 40 },
  commentHeader: { fontSize: 16, color: '#333', marginBottom: 20 },
  commentItem: { flexDirection: 'row', marginBottom: 15 },
  commentAvatar: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#F0F0F0', justifyContent: 'center', alignItems: 'center', marginRight: 10 },
  commentAuthorRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  commentAuthor: { fontSize: 14, color: '#333', marginRight: 8 },
  commentTime: { fontSize: 12, color: '#999' },
  commentText: { fontSize: 14, color: '#444', lineHeight: 20, marginBottom: 8 },
  
  commentActionRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  commentActionBtn: { flexDirection: 'row', alignItems: 'center' },
  commentActionText: { fontSize: 12, color: '#888', marginLeft: 4 },

  repliesContainer: { paddingLeft: 42, marginBottom: 15 },
  replyItem: { flexDirection: 'row', marginBottom: 12, position: 'relative' },
  replyIcon: { position: 'absolute', left: -20, top: 4 },
  
  replyBanner: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#F5F6F8', paddingHorizontal: 15, paddingVertical: 10, borderTopWidth: 1, borderTopColor: '#EEE' },
  replyBannerText: { fontSize: 13, color: '#555' },

  inputContainer: { flexDirection: 'row', padding: 10, paddingBottom: Platform.OS === 'ios' ? 20 : 10, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', alignItems: 'center' },
  input: { flex: 1, minHeight: 40, maxHeight: 100, backgroundColor: '#F5F6F8', borderRadius: 20, paddingHorizontal: 15, paddingTop: 10, paddingBottom: 10, fontSize: 14, marginRight: 10 },
  submitBtn: { backgroundColor: '#1B854A', paddingHorizontal: 15, paddingVertical: 10, borderRadius: 20, justifyContent: 'center' },
  submitBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 14 }
});