import React, { useState, useEffect } from 'react';
import { 
  StyleSheet, View, FlatList, Image, 
  TouchableOpacity, ActivityIndicator, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../components/CustomText';

export default function ReservationsScreen() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [reservationList, setReservationList] = useState<any[]>([]);

  useEffect(() => {
    fetchReservations();
  }, []);

  const fetchReservations = async () => {
    try {
      setIsLoading(true);
      
      setTimeout(() => {
        setReservationList([
          {
            orderId: 102, // 👈 이 ID를 파라미터로 넘겨줍니다.
            shopId: 4,
            shopName: '이음 김치찌개',
            orderDate: '2026-06-15 18:30', 
            status: 'RESERVED',
            amount: 24000,
            thumbnailUrl: 'https://via.placeholder.com/150/E8F5E9/00A859?text=Shop4'
          }
        ]);
        setIsLoading(false);
      }, 400);

    } catch (error) {
      console.error('예약 내역 로딩 실패:', error);
      setIsLoading(false);
    }
  };

  const handleCancelReservation = (orderId: number) => {
    Alert.alert(
      '예약 취소',
      '정말로 이 예약을 취소하시겠습니까?',
      [
        { text: '아니오', style: 'cancel' },
        { 
          text: '예', 
          onPress: () => {
            Alert.alert('알림', '예약이 취소되었습니다.');
            setReservationList(prev => prev.filter(r => r.orderId !== orderId));
          } 
        }
      ]
    );
  };

  const renderReservationItem = ({ item }: { item: any }) => (
    <TouchableOpacity 
      style={styles.cardContainer}
      // ✨ 예약 완료 상세페이지(reservation-detail.tsx)로 이동하며 쿼리 스트링으로 예약 ID를 전달합니다.
      onPress={() => router.push(`/reservation-detail?id=${item.orderId}` as any)}
    >
      <View style={styles.cardHeader}>
        <View style={styles.headerLeft}>
          <Ionicons name="calendar-outline" size={14} color="#2196F3" style={{ marginRight: 4 }} />
          <Text style={styles.dateText}>{item.orderDate} 방문 예정</Text>
        </View>
        <View style={[styles.statusBadge, { backgroundColor: '#E3F2FD' }]}>
          <Text style={[styles.statusText, { color: '#2196F3' }]}>예약 완료</Text>
        </View>
      </View>

      <View style={styles.cardBody}>
        <Image 
          source={{ uri: item.thumbnailUrl || 'https://via.placeholder.com/150' }} 
          style={styles.cardImage} 
        />
        <View style={styles.cardInfo}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.shopName}</Text>
          <Text style={styles.amountText}>결제 금액: {item.amount.toLocaleString()}원</Text>
        </View>
      </View>

      <TouchableOpacity 
        style={styles.cancelButton}
        onPress={() => handleCancelReservation(item.orderId)}
      >
        <Text fontWeight="bold" style={styles.cancelButtonText}>예약 취소하기</Text>
      </TouchableOpacity>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>동네 상점 예약 내역</Text>
        <View style={{ width: 24 }} /> 
      </View>

      {isLoading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : reservationList.length === 0 ? (
        <View style={styles.centerContainer}>
          <Ionicons name="calendar-clear-outline" size={60} color="#DDD" style={{ marginBottom: 15 }} />
          <Text style={styles.emptyText}>진행 중인 예약 내역이 없어요.</Text>
        </View>
      ) : (
        <FlatList
          data={reservationList}
          keyExtractor={(item) => item.orderId.toString()}
          renderItem={renderReservationItem}
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
  headerLeft: { flexDirection: 'row', alignItems: 'center' },
  dateText: { fontSize: 13, color: '#2196F3', fontWeight: '600' },
  statusBadge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  statusText: { fontSize: 12, fontWeight: 'bold' },
  cardBody: { flexDirection: 'row', alignItems: 'center' },
  cardImage: { width: 60, height: 60, borderRadius: 8, marginRight: 15 },
  cardInfo: { flex: 1, justifyContent: 'center' },
  shopName: { fontSize: 16, color: '#333', marginBottom: 6 },
  amountText: { fontSize: 14, color: '#555' },
  cancelButton: { 
    marginTop: 12, backgroundColor: '#FFF5F5', paddingVertical: 12, 
    borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#FF5252' 
  },
  cancelButtonText: { color: '#FF5252', fontSize: 14 }
});