import React, { useCallback, useState } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';
import { UsedReviewItem } from '../../components/used/UsedReviewItem';
import { usedReviewApi, UsedReview } from '../../api/usedReview';
import { getApiErrorMessage } from '../../utils/apiError';

/** 내가 쓴 중고거래 후기. 여기서 수정·삭제까지 한다. */
export default function MyUsedReviewsScreen() {
  const router = useRouter();

  const [reviews, setReviews] = useState<UsedReview[]>([]);
  const [cursor, setCursor] = useState<{ value: string | null; id: number | null }>({ value: null, id: null });
  const [hasNext, setHasNext] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // 수정하고 돌아오면 바뀐 내용이 보여야 한다.
  useFocusEffect(
    useCallback(() => {
      let cancelled = false;

      const load = async () => {
        setIsLoading(true);
        try {
          const slice = await usedReviewApi.getMyReviews();
          if (cancelled) return;
          setReviews(slice.content);
          setHasNext(slice.hasNext);
          setCursor({ value: slice.nextCursorValue, id: slice.nextCursorId });
        } catch (error) {
          console.error('내 후기 조회 실패:', error);
        } finally {
          if (!cancelled) setIsLoading(false);
        }
      };

      load();
      return () => { cancelled = true; };
    }, [])
  );

  const loadMore = async () => {
    if (!hasNext || isLoadingMore) return;

    setIsLoadingMore(true);
    try {
      const slice = await usedReviewApi.getMyReviews({
        cursorValue: cursor.value,
        cursorId: cursor.id,
      });
      setReviews(prev => [...prev, ...slice.content]);
      setHasNext(slice.hasNext);
      setCursor({ value: slice.nextCursorValue, id: slice.nextCursorId });
    } catch (error) {
      console.error('내 후기 추가 조회 실패:', error);
    } finally {
      setIsLoadingMore(false);
    }
  };

  const handleEdit = (review: UsedReview) => {
    router.push(
      `/used-trade/review/write?reviewId=${review.usedReviewId}` +
      `&rating=${review.rating}&content=${encodeURIComponent(review.content)}` as any
    );
  };

  const handleDelete = (review: UsedReview) => {
    Alert.alert('후기 삭제', '삭제한 후기는 되돌릴 수 없어요. 삭제할까요?', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: async () => {
          try {
            await usedReviewApi.deleteReview(review.usedReviewId);
            setReviews(prev => prev.filter(r => r.usedReviewId !== review.usedReviewId));
          } catch (error) {
            Alert.alert('삭제 실패', getApiErrorMessage(error, '잠시 후 다시 시도해 주세요.'));
          }
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIcon}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>내가 쓴 거래 후기</Text>
        <View style={{ width: 40 }} />
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>
      ) : (
        <FlatList
          data={reviews}
          keyExtractor={(item) => String(item.usedReviewId)}
          renderItem={({ item }) => (
            <UsedReviewItem review={item} onEdit={handleEdit} onDelete={handleDelete} />
          )}
          contentContainerStyle={styles.listContent}
          onEndReached={loadMore}
          onEndReachedThreshold={0.4}
          ListEmptyComponent={
            <View style={styles.center}>
              <Ionicons name="chatbubble-ellipses-outline" size={40} color="#DDD" style={{ marginBottom: 12 }} />
              <Text style={styles.emptyText}>아직 쓴 거래 후기가 없어요.</Text>
            </View>
          }
          ListFooterComponent={
            isLoadingMore ? <ActivityIndicator style={{ marginVertical: 20 }} color="#00A859" /> : null
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 8, height: 56, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  headerIcon: { padding: 8 },
  headerTitle: { fontSize: 17, color: '#333' },
  center: { alignItems: 'center', justifyContent: 'center', paddingVertical: 60 },
  emptyText: { fontSize: 15, color: '#999' },
  listContent: { paddingHorizontal: 20, paddingBottom: 40 },
});
