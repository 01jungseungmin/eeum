import React, { useState, useCallback } from 'react';
import { 
  View, StyleSheet, FlatList, TouchableOpacity, 
  ActivityIndicator, Image, Alert, RefreshControl 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { reviewApi } from '../../api/review';

type ReviewType = 'ALL' | 'ORDER' | 'RESERVATION';

const TABS: { id: ReviewType; name: string }[] = [
  { id: 'ALL', name: '전체' },
  { id: 'ORDER', name: '주문 내역' },
  { id: 'RESERVATION', name: '예약 내역' }
];

export default function MyReviewsScreen() {
  const router = useRouter();

  const [reviews, setReviews] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState<ReviewType>('ALL');
  
  // 실제 백엔드 연동을 위한 상태 관리
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [page, setPage] = useState(0);
  const [isLastPage, setIsLastPage] = useState(false);
  const [isFetchingMore, setIsFetchingMore] = useState(false);

  // 탭이 변경되거나 화면에 포커스가 올 때 1페이지(0)부터 다시 로딩합니다.
  useFocusEffect(
    useCallback(() => {
      fetchMyReviews(activeTab, 0);
    }, [activeTab])
  );

  // 백엔드 API와 통신하여 데이터를 가져오고 페이징을 처리하는 핵심 함수
  const fetchMyReviews = async (type: ReviewType, pageNum: number, isRefresh = false) => {
    try {
      if (isRefresh) setIsRefreshing(true);
      else if (pageNum === 0) setIsLoading(true);
      else setIsFetchingMore(true);

      const apiType = type === 'ALL' ? undefined : type;
      // 페이지 번호(pageNum)와 사이즈(20)를 백엔드에 넘겨줍니다.
      const res = await reviewApi.getMyReviews(apiType, pageNum, 20);
      
      // 백엔드 응답(Page 객체)에서 실제 배열과 마지막 페이지 여부를 추출합니다.
      const content = res?.data?.content || res?.content || res || [];
      const last = res?.data?.last ?? res?.last ?? true;

      if (pageNum === 0) {
        setReviews(content); // 첫 페이지면 덮어쓰기
      } else {
        setReviews(prev => [...prev, ...content]); // 다음 페이지면 뒤에 이어붙이기
      }

      setIsLastPage(last);
      setPage(pageNum);

    } catch (error) {
      console.error('내 리뷰 로딩 실패:', error);
      if (pageNum === 0) setReviews([]); // 에러 시 잔상 방지
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
      setIsFetchingMore(false);
    }
  };

  // 당겨서 새로고침 기능
  const handleRefresh = () => {
    fetchMyReviews(activeTab, 0, true);
  };

  // 스크롤이 끝에 닿았을 때 다음 페이지 호출
  const handleLoadMore = () => {
    if (!isLastPage && !isFetchingMore && !isLoading) {
      fetchMyReviews(activeTab, page + 1);
    }
  };

  const handleDeleteReview = (storeId: number, reviewId: number) => {
    Alert.alert(
      '리뷰 삭제',
      '정말로 이 리뷰를 삭제하시겠습니까?',
      [
        { text: '취소', style: 'cancel' },
        { 
          text: '삭제', 
          style: 'destructive',
          onPress: async () => {
            try {
              await reviewApi.deleteReview(storeId, reviewId);
              Alert.alert('알림', '리뷰가 삭제되었습니다.');
              // 삭제 성공 후 현재 탭의 1페이지부터 다시 불러와서 목록 최신화
              fetchMyReviews(activeTab, 0, true); 
            } catch (error) {
              Alert.alert('오류', '리뷰 삭제에 실패했습니다.');
            }
          }
        }
      ]
    );
  };

  const handleEditReview = (item: any) => {
    router.push({
      pathname: '/review/write',
      params: {
        storeId: item.storeId,
        reviewId: item.storereviewId,
        initialRating: item.rating,
        initialContent: item.content,
        initialImageId: item.images && item.images.length > 0 ? item.images[0].imageId : '',
        initialImageUrl: item.images && item.images.length > 0 ? item.images[0].imageUrl : '',
      }
    } as any);
  };

  const renderReview = ({ item }: { item: any }) => {
    const isOrder = item.reviewType === 'ORDER';
    const badgeText = isOrder ? '주문' : '예약';
    const badgeColor = isOrder ? '#E3F2FD' : '#FCE4EC';
    const badgeTextColor = isOrder ? '#1976D2' : '#C2185B';

    return (
      <TouchableOpacity 
        style={styles.card}
        activeOpacity={0.8}
        onPress={() => router.push(`/shop/${item.storeId}` as any)}
      >
        <View style={styles.cardHeader}>
          <View style={styles.headerLeft}>
            <View style={[styles.badge, { backgroundColor: badgeColor }]}>
              <Text fontWeight="bold" style={[styles.badgeText, { color: badgeTextColor }]}>
                {badgeText}
              </Text>
            </View>
            <Text fontWeight="bold" style={styles.storeName} numberOfLines={1}>
              {item.storeName}
            </Text>
            <Ionicons name="chevron-forward" size={14} color="#999" />
          </View>
          
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <Text style={styles.dateText}>
              {item.createdAt ? item.createdAt.substring(0, 10) : ''}
            </Text>

            <TouchableOpacity 
              style={{ marginLeft: 12 }}
              onPress={(e) => {
                e.stopPropagation(); 
                handleEditReview(item);
              }}
            >
              <Ionicons name="pencil-outline" size={16} color="#666" />
            </TouchableOpacity>

            <TouchableOpacity 
              style={{ marginLeft: 12 }}
              onPress={(e) => {
                e.stopPropagation(); 
                handleDeleteReview(item.storeId, item.storereviewId);
              }}
            >
              <Ionicons name="trash-outline" size={16} color="#FF5252" />
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.ratingRow}>
          <Ionicons name="star" size={14} color="#FFD700" />
          <Text fontWeight="bold" style={styles.ratingText}>{item.rating?.toFixed(1) || '0.0'}</Text>
        </View>

        <Text style={styles.content} numberOfLines={3}>
          {item.content}
        </Text>

        {item.images && item.images.length > 0 && item.images[0]?.imageUrl && (
          <Image source={{ uri: item.images[0].imageUrl }} style={styles.reviewImage} />
        )}
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>작성한 리뷰</Text>
        <View style={{ width: 26 }} />
      </View>

      <View style={styles.tabContainer}>
        {TABS.map((tab) => (
          <TouchableOpacity
            key={tab.id}
            style={[styles.tabItem, activeTab === tab.id && styles.activeTabItem]}
            onPress={() => setActiveTab(tab.id)}
          >
            <Text 
              fontWeight={activeTab === tab.id ? 'bold' : 'normal'}
              style={[styles.tabText, activeTab === tab.id && styles.activeTabText]}
            >
              {tab.name}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : reviews.length === 0 ? (
        <View style={styles.center}>
          <Ionicons name="document-text-outline" size={48} color="#DDD" style={{ marginBottom: 12 }} />
          <Text style={styles.emptyText}>작성한 리뷰가 없습니다.</Text>
        </View>
      ) : (
        <FlatList
          data={reviews}
          keyExtractor={(item) => item.storereviewId?.toString() || Math.random().toString()}
          renderItem={renderReview}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} colors={['#1B854A']} />
          }
          onEndReached={handleLoadMore}
          onEndReachedThreshold={0.5}
          ListFooterComponent={isFetchingMore ? <ActivityIndicator style={{ padding: 20 }} color="#1B854A" /> : null}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyText: { color: '#999', fontSize: 15 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backButton: { padding: 5 },
  headerTitle: { fontSize: 18, color: '#333' },
  tabContainer: { flexDirection: 'row', backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#EEE' },
  tabItem: { flex: 1, alignItems: 'center', paddingVertical: 14, borderBottomWidth: 2, borderBottomColor: 'transparent' },
  activeTabItem: { borderBottomColor: '#1B854A' },
  tabText: { fontSize: 14, color: '#888' },
  activeTabText: { color: '#1B854A' },
  listContainer: { padding: 15 },
  card: { backgroundColor: '#FFF', borderRadius: 8, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: '#EAEAEA', shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.03, shadowRadius: 2, elevation: 1 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  headerLeft: { flexDirection: 'row', alignItems: 'center', flex: 1, paddingRight: 10 },
  badge: { paddingHorizontal: 6, paddingVertical: 3, borderRadius: 4, marginRight: 6 },
  badgeText: { fontSize: 11 },
  storeName: { fontSize: 15, color: '#333', marginRight: 4, maxWidth: '70%' },
  dateText: { fontSize: 12, color: '#999' },
  ratingRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  ratingText: { fontSize: 13, color: '#333', marginLeft: 4 },
  content: { fontSize: 14, color: '#444', lineHeight: 20 },
  reviewImage: { width: 80, height: 80, borderRadius: 8, marginTop: 12, backgroundColor: '#F5F5F5' }
});