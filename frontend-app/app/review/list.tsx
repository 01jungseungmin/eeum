import React, { useState, useEffect } from 'react';
import { View, StyleSheet, FlatList, ActivityIndicator, TouchableOpacity, Image } from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { reviewApi } from '../../api/review';

export default function ReviewListScreen() {
  const router = useRouter();
  const { storeId } = useLocalSearchParams();
  const storeIdNum = typeof storeId === 'string' ? Number(storeId) : 0;

  const [reviews, setReviews] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchReviews = async () => {
      try {
        setIsLoading(true);
        const res = await reviewApi.getReviews(storeIdNum);
        // 스웨거 구조에 맞게 res.content 가져오기
        setReviews(res?.content || []);
      } catch (error) {
        console.error("리뷰 목록 로딩 에러:", error);
      } finally {
        setIsLoading(false);
      }
    };
    if (storeIdNum) fetchReviews();
  }, [storeIdNum]);

  const renderReview = ({ item }: any) => (
    <View style={styles.reviewCard}>
      <View style={styles.reviewUserRow}>
        <Ionicons name="star" size={14} color="#FFD700" />
        <Text style={styles.reviewRatingText}>{item.rating?.toFixed(1) || '0.0'}</Text>
        {/* ✨ writerName 대신 nickname 사용 */}
        <Text style={styles.reviewWriterText}>{item.nickname || '익명'}</Text>
        <Text style={styles.reviewDateText}>{item.createdAt?.split('T')[0]}</Text>
      </View>
      <Text style={styles.reviewContentText}>{item.content}</Text>
      {item.images && item.images.length > 0 && (
        <Image source={{ uri: item.images[0].imageUrl }} style={styles.reviewImage} />
      )}
      
      {/* ✨ 사장님 답글 영역 (데이터에 reply가 있으면 노출) */}
      {item.reply && (
        <View style={styles.replyBox}>
          <Text fontWeight="bold" style={styles.replyNickname}>{item.reply.nickname}</Text>
          <Text style={styles.replyContent}>{item.reply.content}</Text>
        </View>
      )}
    </View>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 10 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>상점 리뷰</Text>
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>
      ) : reviews.length === 0 ? (
        <View style={styles.center}>
          <Ionicons name="chatbubble-outline" size={48} color="#CCC" style={{ marginBottom: 10 }} />
          <Text style={{ color: '#888', fontSize: 16 }}>아직 등록된 리뷰가 없습니다.</Text>
        </View>
      ) : (
        <FlatList
          data={reviews}
          // ✨ reviewId 대신 storereviewId 사용
          keyExtractor={(item) => item.storereviewId?.toString()}
          renderItem={renderReview}
          contentContainerStyle={{ padding: 20 }}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  headerTitle: { fontSize: 18, color: '#333' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  reviewCard: { marginBottom: 20, borderBottomWidth: 1, borderBottomColor: '#F0F0F0', paddingBottom: 20 },
  reviewUserRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  reviewRatingText: { fontWeight: 'bold', marginLeft: 4, fontSize: 14, color: '#333' },
  reviewWriterText: { color: '#666', marginLeft: 10, fontSize: 13, fontWeight: '500' },
  reviewDateText: { color: '#999', marginLeft: 'auto', fontSize: 12 },
  reviewContentText: { fontSize: 14, color: '#333', lineHeight: 22 },
  reviewImage: { width: 100, height: 100, borderRadius: 8, marginTop: 12 },
  // 사장님 답글 스타일
  replyBox: { marginTop: 12, padding: 12, backgroundColor: '#F5F5F5', borderRadius: 8 },
  replyNickname: { fontSize: 13, color: '#333', marginBottom: 4 },
  replyContent: { fontSize: 13, color: '#666', lineHeight: 18 }
});