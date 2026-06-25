import React, { useState, useCallback } from 'react';
import { 
  StyleSheet, View, FlatList, Image, 
  TouchableOpacity, ActivityIndicator 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
// ✨ useEffect 대신 useFocusEffect를 가져옵니다.
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { orderApi } from '@/api/order';

export default function HistoryScreen() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [historyList, setHistoryList] = useState<any[]>([]);

  // ✨ 기존 useEffect를 지우고 useFocusEffect로 교체합니다.
  // 이제 화면에 돌아올 때마다 fetchHistory가 다시 실행되어 '리뷰 작성 완료' 상태가 즉시 반영됩니다.
  useFocusEffect(
    useCallback(() => {
      fetchHistory();
    }, [])
  );

  const fetchHistory = async () => {
    try {
      setIsLoading(true);
      
      const res = await orderApi.getMyHistory(); 
      const realData = res.data?.content || res.data?.data || [];
      setHistoryList(realData);

      console.log("🔥 실제 거래 내역 데이터:", JSON.stringify(realData[0], null, 2));

    } catch (error) {
      console.error('거래 내역 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const getStatusStyle = (status: string) => {
    if (status === 'COMPLETED') {
      return { text: '이용 완료', color: '#00A859', bgColor: '#E8F5E9' };
    }
    return { text: '취소/환불', color: '#FF5252', bgColor: '#FFEBEE' };
  };

  const renderHistoryItem = ({ item }: { item: any }) => {
    const statusStyle = getStatusStyle(item.status);

    const formattedDate = item.paidAt 
      ? item.paidAt.replace('T', ' ').substring(0, 16) 
      : '결제일시 없음';
      
    const imageUrl = item.items?.[0]?.thumbnailUrl || 'https://via.placeholder.com/150';

    const isReviewCompleted = item.hasReview === true;

    return (
      <TouchableOpacity 
        style={styles.cardContainer}
        onPress={() => router.push(`/order/${item.orderId}` as any)}
      >
        <View style={styles.cardHeader}>
          <Text style={styles.dateText}>{formattedDate}</Text>
          <View style={[styles.statusBadge, { backgroundColor: statusStyle.bgColor }]}>
            <Text style={[styles.statusText, { color: statusStyle.color }]}>
              {statusStyle.text}
            </Text>
          </View>
        </View>

        <View style={styles.cardBody}>
          <Image 
            source={{ uri: imageUrl }} 
            style={styles.cardImage} 
          />
          <View style={styles.cardInfo}>
            <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>
              {item.storeName}
            </Text>
            <Text style={styles.amountText}>
              {item.totalPrice?.toLocaleString() || 0}원
            </Text>
          </View>
        </View>

        {item.status === 'COMPLETED' && (
          isReviewCompleted ? (
            <View style={[styles.reviewButton, { backgroundColor: '#F5F5F5', borderColor: '#EEE' }]}>
              <Text fontWeight="bold" style={[styles.reviewButtonText, { color: '#999' }]}>리뷰 작성 완료</Text>
            </View>
          ) : (
            <TouchableOpacity 
              style={styles.reviewButton}
              onPress={(e) => {
                e.stopPropagation(); 
                router.push(`/review/write?storeId=${item.storeId}&orderId=${item.orderId}`);
              }}
            >
              <Text fontWeight="bold" style={styles.reviewButtonText}>리뷰 작성하기</Text>
            </TouchableOpacity>
          )
        )}
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>동네 상점 거래 내역</Text>
        <View style={{ width: 24 }} /> 
      </View>

      {isLoading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : historyList.length === 0 ? (
        <View style={styles.centerContainer}>
          <Ionicons name="receipt-outline" size={60} color="#DDD" style={{ marginBottom: 15 }} />
          <Text style={styles.emptyText}>거래 내역이 없어요.</Text>
        </View>
      ) : (
        <FlatList
          data={historyList}
          keyExtractor={(item) => item.orderId.toString()}
          renderItem={renderHistoryItem}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    paddingHorizontal: 20, paddingVertical: 15, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#F0F0F0'
  },
  backButton: { padding: 4, marginLeft: -4 },
  headerTitle: { fontSize: 18, color: '#333' },
  centerContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F8F9FA' },
  emptyText: { fontSize: 16, color: '#666', fontWeight: 'bold' },
  listContainer: { padding: 20 },
  cardContainer: { 
    backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 15,
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 5, elevation: 3
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, borderBottomWidth: 1, borderBottomColor: '#F0F0F0', paddingBottom: 10 },
  dateText: { fontSize: 13, color: '#888' },
  statusBadge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  statusText: { fontSize: 12, fontWeight: 'bold' },
  cardBody: { flexDirection: 'row', alignItems: 'center' },
  cardImage: { width: 60, height: 60, borderRadius: 8, marginRight: 15 },
  cardInfo: { flex: 1, justifyContent: 'center' },
  shopName: { fontSize: 16, color: '#333', marginBottom: 6 },
  amountText: { fontSize: 14, color: '#555', fontWeight: '600' },
  reviewButton: { 
    marginTop: 12, backgroundColor: '#F0F9F4', paddingVertical: 12, 
    borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#00A859' 
  },
  reviewButtonText: { color: '#00A859', fontSize: 14 }
});