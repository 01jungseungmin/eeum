import React, { useState, useEffect, useRef, useCallback } from 'react';
import { View, StyleSheet, TouchableOpacity, TextInput, KeyboardAvoidingView, Platform, ScrollView, ActivityIndicator, Alert, Image, Keyboard } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';
import { userApi } from '../../api/user';

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

  const insets = useSafeAreaInsets();
  const [replyTarget, setReplyTarget] = useState<{ commentId: number; authorName: string } | null>(null);
  const inputRef = useRef<TextInput>(null);

  const [isKeyboardVisible, setKeyboardVisible] = useState(false);
  const [myAccountId, setMyAccountId] = useState<number | null>(null);

  useEffect(() => {
    const fetchMyInfo = async () => {
      try {
        const myInfo = await userApi.getMyInfo();
        setMyAccountId(myInfo.accountId);
      } catch (error) {
        console.error('내 정보 로딩 실패:', error);
      }
    };
    fetchMyInfo();
  }, []);

  // 1. 데이터를 불러오는 함수를 useCallback으로 감싸서 메모리 갇힘(Closure) 현상을 박살냅니다!
  const fetchDetailAndComments = useCallback(async () => {
    if (!id) return;
    try {
      setIsLoading(true);
      const [postData, commentsData] = await Promise.all([
        communityApi.getPostDetail(id as string),
        communityApi.getComments(id as string)
      ]);

      const commentsWithReplies = await Promise.all(
        commentsData.map(async (comment: any) => {
          try {
            const currentCommentId = comment.commentId || comment.id;
            const replyData = await communityApi.getReplies(currentCommentId);
            return { ...comment, replies: replyData };
          } catch (e) {
            return { ...comment, replies: [] };
          }
        })
      );

      setPost(postData);
      setIsLiked(postData.likedByMe);
      setLikeCount(postData.likeCount);
      setComments(commentsWithReplies);
    } catch (error) {
      console.error('상세 정보 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  // 2. 이제 화면이 다시 보일 때마다 위에서 만든 '무조건 최신화' 함수가 실행됩니다!
  useFocusEffect(
    useCallback(() => {
      fetchDetailAndComments();
    }, [fetchDetailAndComments])
  );

  useEffect(() => {
    const keyboardDidShowListener = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow',
      () => setKeyboardVisible(true)
    );
    const keyboardDidHideListener = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide',
      () => setKeyboardVisible(false)
    );

    return () => {
      keyboardDidShowListener.remove();
      keyboardDidHideListener.remove();
    };
  }, []);

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

  const handleToggleReplyLike = async (commentId: number, replyId: number, currentIsLiked: boolean) => {
    // 1. 프론트엔드 상태 먼저 업데이트 (빠른 UI 반응)
    setComments((prevComments) =>
      prevComments.map((comment) => {
        if (comment.commentId === commentId || comment.id === commentId) {
          return {
            ...comment,
            replies: comment.replies.map((reply: any) => {
              const currentReplyId = reply.replyId || reply.commentId || reply.id;
              if (currentReplyId === replyId) {
                return {
                  ...reply,
                  likedByMe: !currentIsLiked,
                  likeCount: currentIsLiked ? (reply.likeCount || 1) - 1 : (reply.likeCount || 0) + 1,
                };
              }
              return reply;
            }),
          };
        }
        return comment;
      })
    );

    // 2. 백엔드 API 호출 (에러 시 원상복구)
    try {
      if (currentIsLiked) {
        // [주의] 백엔드 스펙에 맞춰 대댓글 전용 API가 있다면 교체하세요 (예: communityApi.unlikeReply)
        await communityApi.unlikeComment(replyId); 
      } else {
        await communityApi.likeComment(replyId);
      }
    } catch (error) {
      console.error('대댓글 좋아요 실패', error);
      // 에러 시 상태 원상복구 로직...
      fetchDetailAndComments(); // 제일 안전한 복구 방식
      Alert.alert('오류', '대댓글 좋아요 처리에 실패했습니다.');
    }
  };

  const handleSubmitComment = async () => {
    if (!inputText.trim()) return;
    
    try {
      setIsSubmitting(true);
      
      if (replyTarget) {
        await communityApi.createReply(replyTarget.commentId, inputText);
      } else {
        await communityApi.createComment(id as string, inputText);
      }
      
      setInputText('');
      setReplyTarget(null);
      Keyboard.dismiss(); 
      
      await fetchDetailAndComments(); 
      
    } catch (error: any) {
      Alert.alert('작성 실패', '오류가 발생했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeletePost = () => {
    Alert.alert(
      '게시글 삭제',
      '정말 이 게시글을 삭제하시겠습니까?',
      [
        { text: '취소', style: 'cancel' },
        { 
          text: '삭제', 
          style: 'destructive',
          onPress: async () => {
            try {
              setIsLoading(true);
              await communityApi.deletePost(id as string);
              Alert.alert('알림', '게시글이 삭제되었습니다.');
              router.back(); 
            } catch (error) {
              console.error('게시글 삭제 실패:', error);
              Alert.alert('오류', '게시글 삭제에 실패했습니다.');
              setIsLoading(false);
            }
          } 
        }
      ]
    );
  };

  const handleOpenPostOptions = () => {
    Alert.alert(
      '게시글 관리',
      '무엇을 하시겠습니까?',
      [
        { 
          text: '수정하기', 
          onPress: () => router.push({
            pathname: '/community/write' as any,
            params: { editId: id }
          }) 
        },
        { text: '삭제하기', onPress: handleDeletePost, style: 'destructive' },
        { text: '취소', style: 'cancel' }
      ]
    );
  };

  const handleDeleteComment = (commentId: number | string) => {
    Alert.alert(
      '댓글 삭제',
      '이 댓글을 삭제하시겠습니까?',
      [
        { text: '취소', style: 'cancel' },
        { 
          text: '삭제', 
          style: 'destructive',
          onPress: async () => {
            try {
              await communityApi.deleteComment(commentId);
              await fetchDetailAndComments(); 
            } catch (error) {
              console.error('댓글 삭제 실패:', error);
              Alert.alert('오류', '댓글 삭제에 실패했습니다.');
            }
          } 
        }
      ]
    );
  };

  if (isLoading || !post) {
    return (
      <SafeAreaView style={styles.centerLoading}>
        <ActivityIndicator size="large" color="#1B854A" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={28} color="#333" />
        </TouchableOpacity>
        <View style={styles.headerIcons}>
          <TouchableOpacity style={{ marginRight: 15 }}><Ionicons name="share-social-outline" size={24} color="#333" /></TouchableOpacity>
          {myAccountId && post?.authorId === myAccountId && (
            <TouchableOpacity onPress={handleOpenPostOptions}>
              <Ionicons name="ellipsis-vertical" size={24} color="#333" />
            </TouchableOpacity>
          )}
        </View>
      </View>

      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior="padding" 
        keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0} 
        enabled={Platform.OS === 'ios' ? true : isKeyboardVisible} 
      >
        <ScrollView style={{ flex: 1 }} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
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
            
            {post.images && post.images.length > 0 && (
              <ScrollView 
                horizontal 
                showsHorizontalScrollIndicator={false} 
                style={{ marginBottom: 15 }}
              >
                {post.images.map((img: any, index: number) => (
                  <Image 
                    key={img.imageId || index}
                    source={{ uri: img.imageUrl }} 
                    style={[styles.postImage, { width: 300, marginRight: 10 }]} 
                    resizeMode="cover" 
                  />
                ))}
              </ScrollView>
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

          <View style={styles.commentSection}>
            <Text fontWeight="bold" style={styles.commentHeader}>댓글 {comments.length}</Text>
            
            {comments.map((comment: any, index) => {
              const currentCommentId = comment.commentId || comment.id; 
              const authorName = comment.authorNickname || comment.authorName || comment.nickname || '익명';
              
              return (
                <View key={currentCommentId || index}>
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
                            setTimeout(() => inputRef.current?.focus(), 100); 
                          }}
                        >
                          <Ionicons name="chatbubble-outline" size={12} color="#888" />
                          <Text style={styles.commentActionText}>답글 달기</Text>
                        </TouchableOpacity>

                        {myAccountId && comment.authorId === myAccountId && (
                          <TouchableOpacity 
                            style={styles.commentActionBtn}
                            onPress={() => handleDeleteComment(currentCommentId)}
                          >
                            <Ionicons name="trash-outline" size={12} color="#888" />
                            <Text style={styles.commentActionText}>삭제</Text>
                          </TouchableOpacity>
                        )}
                      </View>
                    </View>
                  </View>

                  {/* 대댓글 영역 */}
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
                            
                            {/* 대댓글 액션 버튼 영역 추가 */}
                            <View style={styles.commentActionRow}>
                              
                              {/* 1. 대댓글 좋아요 버튼 */}
                              <TouchableOpacity 
                                style={styles.commentActionBtn}
                                onPress={() => handleToggleReplyLike(currentCommentId, replyId, reply.likedByMe)}
                              >
                                <Ionicons name={reply.likedByMe ? "heart" : "heart-outline"} size={12} color={reply.likedByMe ? "#E25555" : "#888"} />
                                <Text style={[styles.commentActionText, reply.likedByMe && { color: "#E25555" }]}>
                                  좋아요 {reply.likeCount > 0 ? reply.likeCount : ''}
                                </Text>
                              </TouchableOpacity>

                              {/* 2. 대댓글 삭제 버튼 (작성자 본인일 때만) */}
                              {myAccountId && reply.authorId === myAccountId && (
                                <TouchableOpacity 
                                  style={styles.commentActionBtn}
                                  onPress={() => handleDeleteComment(replyId)}
                                >
                                  <Ionicons name="trash-outline" size={12} color="#888" />
                                  <Text style={styles.commentActionText}>삭제</Text>
                                </TouchableOpacity>
                              )}
                            </View>

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
        
        <View 
          style={[
            styles.inputContainer, 
            { paddingBottom: isKeyboardVisible ? 0 : (Platform.OS === 'ios' ? 20 : insets.bottom + 10) }
          ]}
        >
          <TextInput
            ref={inputRef} 
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
  
  commentActionRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 4 },
  commentActionBtn: { flexDirection: 'row', alignItems: 'center' },
  commentActionText: { fontSize: 12, color: '#888', marginLeft: 4 },

  repliesContainer: { paddingLeft: 42, marginBottom: 15 },
  replyItem: { flexDirection: 'row', marginBottom: 12, position: 'relative' },
  replyIcon: { position: 'absolute', left: -20, top: 4 },
  
  replyBanner: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#F5F6F8', paddingHorizontal: 15, paddingVertical: 10, borderTopWidth: 1, borderTopColor: '#EEE' },
  replyBannerText: { fontSize: 13, color: '#555' },

  inputContainer: { flexDirection: 'row', padding: 10, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', alignItems: 'center' },
  input: { flex: 1, minHeight: 40, maxHeight: 100, backgroundColor: '#F5F6F8', borderRadius: 20, paddingHorizontal: 15, paddingTop: 10, paddingBottom: 10, fontSize: 14, marginRight: 10 },
  submitBtn: { backgroundColor: '#1B854A', paddingHorizontal: 15, paddingVertical: 10, borderRadius: 20, justifyContent: 'center' },
  submitBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 14 }
});