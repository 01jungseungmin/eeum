import React, { useState, useEffect } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator, Image } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
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
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<ReviewType>('ALL');

  useEffect(() => {
    fetchMyReviews(activeTab);
  }, [activeTab]);

  const fetchMyReviews = async (type: ReviewType) => {
    try {
      setIsLoading(true);
      const apiType = type === 'ALL' ? undefined : type;
      const data = await reviewApi.getMyReviews(apiType, 0, 20);
      setReviews(data);
    } catch (error) {
      console.error('내 리뷰 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
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
          <Text style={styles.dateText}>
            {item.createdAt ? item.createdAt.substring(0, 10) : ''}
          </Text>
        </View>

        <View style={styles.ratingRow}>
          <Ionicons name="star" size={14} color="#FFD700" />
          <Text fontWeight="bold" style={styles.ratingText}>{item.rating?.toFixed(1) || '0.0'}</Text>
        </View>

        <Text style={styles.content} numberOfLines={3}>
          {item.content}
        </Text>

        {item.images && item.images.length > 0 && (
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
          keyExtractor={(item) => item.storereviewId.toString()}
          renderItem={renderReview}
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