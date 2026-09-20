import React, { useCallback, useEffect, useState } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { Text } from '../../../components/CustomText';
import { StarRating } from '../../../components/used/StarRating';
import { UsedReviewItem } from '../../../components/used/UsedReviewItem';
import { usedReviewApi, UsedReview, UsedReviewSummary } from '../../../api/usedReview';

/**
 * 판매자 평판. 비회원도 볼 수 있어야 하므로 로그인 여부를 따지지 않는다.
 */
export default function SellerReviewsScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();
  const sellerId = Number(id);

  const [summary, setSummary] = useState<UsedReviewSummary | null>(null);
  const [reviews, setReviews] = useState<UsedReview[]>([]);
  const [cursor, setCursor] = useState<{ value: string | null; id: number | null }>({ value: null, id: null });
  const [hasNext, setHasNext] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  useEffect(() => {
    if (!sellerId || Number.isNaN(sellerId)) return;

    let cancelled = false;

    const load = async () => {
      setIsLoading(true);
      try {
        const [summaryRes, sliceRes] = await Promise.all([
          usedReviewApi.getSellerReviewSummary(sellerId),
          usedReviewApi.getSellerReviews(sellerId),
        ]);
        if (cancelled) return;

        setSummary(summaryRes);
        setReviews(sliceRes.content);
        setHasNext(sliceRes.hasNext);
        setCursor({ value: sliceRes.nextCursorValue, id: sliceRes.nextCursorId });
      } catch (error) {
        console.error('판매자 후기 조회 실패:', error);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    load();
    return () => { cancelled = true; };
  }, [sellerId]);

  const loadMore = useCallback(async () => {
    if (!hasNext || isLoadingMore) return;

    setIsLoadingMore(true);
    try {
      const slice = await usedReviewApi.getSellerReviews(sellerId, {
        cursorValue: cursor.value,
        cursorId: cursor.id,
      });
      setReviews(prev => [...prev, ...slice.content]);
      setHasNext(slice.hasNext);
      setCursor({ value: slice.nextCursorValue, id: slice.nextCursorId });
    } catch (error) {
      console.error('판매자 후기 추가 조회 실패:', error);
    } finally {
      setIsLoadingMore(false);
    }
  }, [hasNext, isLoadingMore, sellerId, cursor]);

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Stack.Screen options={{ headerShown: false }} />

      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIcon}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>판매자 후기</Text>
        <View style={{ width: 40 }} />
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>
      ) : (
        <FlatList
          data={reviews}
          keyExtractor={(item) => String(item.usedReviewId)}
          renderItem={({ item }) => <UsedReviewItem review={item} />}
          contentContainerStyle={styles.listContent}
          onEndReached={loadMore}
          onEndReachedThreshold={0.4}
          ListHeaderComponent={
            <View style={styles.summary}>
              <Text style={styles.average}>
                {summary && summary.reviewCount > 0
                  ? Number(summary.averageRating ?? 0).toFixed(1)
                  : '-'}
              </Text>
              <StarRating rating={summary?.averageRating ?? 0} size={18} />
              <Text style={styles.count}>후기 {summary?.reviewCount ?? 0}개</Text>
            </View>
          }
          ListEmptyComponent={
            <View style={styles.center}>
              <Ionicons name="chatbubble-ellipses-outline" size={40} color="#DDD" style={{ marginBottom: 12 }} />
              <Text style={styles.emptyText}>아직 받은 후기가 없어요.</Text>
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
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 8, height: 56 },
  headerIcon: { padding: 8 },
  headerTitle: { fontSize: 17, color: '#333' },
  center: { alignItems: 'center', justifyContent: 'center', paddingVertical: 60 },
  emptyText: { fontSize: 15, color: '#999' },
  listContent: { paddingHorizontal: 20, paddingBottom: 40 },
  summary: { alignItems: 'center', paddingVertical: 28, gap: 8 },
  average: { fontSize: 36, fontWeight: 'bold', color: '#1E3932' },
  count: { fontSize: 13, color: '#999' },
});
