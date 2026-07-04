import React, { useState, useCallback } from 'react';
import { 
  StyleSheet, View, FlatList, Image, 
  TouchableOpacity, ActivityIndicator, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';

import { reservationApi } from '@/api/reservation'; 
import { reviewApi } from '../../api/review'; 

export default function ReservationsScreen() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [reservationList, setReservationList] = useState<any[]>([]);

  useFocusEffect(
    useCallback(() => {
      fetchReservations();
    }, [])
  );

  const fetchReservations = async () => {
    try {
      setIsLoading(true);
      const res = await reservationApi.getMyVisitReservations();
      
      const realData = res?.content || res?.data || res || [];
      
      // ✨ [추가] 받아온 예약 목록 중 'COMPLETED'인 항목들만 리뷰 작성 여부를 확인합니다.
      const updatedData = await Promise.all(
        realData.map(async (item: any) => {
          if (item.status === 'COMPLETED') {
            try {
              // 리뷰 상세 조회 API 호출
              const reviewData = await reviewApi.getReservationReview(item.visitReservationId);
              // 데이터가 존재하면 리뷰 작성 완료 처리
              if (reviewData && reviewData.storereviewId) {
                return { ...item, isReviewed: true };
              }
            } catch (e) {
              // 에러(404 등)가 나면 아직 리뷰를 안 쓴 것
              return { ...item, isReviewed: false };
            }
          }
          // COMPLETED가 아니면 기존 데이터 그대로 리턴
          return item;
        })
      );

      setReservationList(updatedData);
    } catch (error) {
      console.error('예약 내역 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCancelReservation = (reservationId: number) => {
    Alert.alert(
      '예약 취소',
      '정말로 이 예약을 취소하시겠습니까?',
      [
        { text: '아니오', style: 'cancel' },
        { 
          text: '예', 
          onPress: async () => {
            try {
              await reservationApi.cancelVisitReservation(reservationId);
              Alert.alert('알림', '예약이 정상적으로 취소되었습니다.');
              fetchReservations(); 
            } catch (error) {
              Alert.alert('오류', '예약 취소에 실패했습니다.');
            }
          } 
        }
      ]
    );
  };

  const renderReservationItem = ({ item }: { item: any }) => {
    const rId = item.visitReservationId; 
    const storeName = item.storeName;
    
    const timeText = item.visitTime ? item.visitTime.substring(0, 5) : '';
    const dateText = `${item.visitDate} ${timeText}`;

    let statusText = '예약 대기';
    let statusColor = '#FF9800'; 
    let statusBg = '#FFF3E0';

    if (item.status === 'APPROVED' || item.status === 'RESERVED' || item.status === 'CONFIRMED') {
      statusText = '예약 확정';
      statusColor = '#2196F3';
      statusBg = '#E3F2FD';
    } else if (item.status === 'COMPLETED') {
      statusText = '이용 완료';
      statusColor = '#00A859';
      statusBg = '#E8F5E9';
    } else if (item.status === 'CANCELLED' || item.status === 'REJECTED') {
      statusText = item.status === 'CANCELLED' ? '예약 취소' : '예약 거절';
      statusColor = '#FF5252';
      statusBg = '#FFEBEE';
    }

    const isReviewCompleted = item.isReviewed === true;

    return (
      <TouchableOpacity 
        style={styles.cardContainer}
        // 상세 페이지 이동 유지
        onPress={() => router.push(`/restaurant/reservation-detail?id=${rId}` as any)}
      >
        <View style={styles.cardHeader}>
          <View style={styles.headerLeft}>
            <Ionicons name="calendar-outline" size={14} color={statusColor} style={{ marginRight: 4 }} />
            <Text style={[styles.dateText, { color: statusColor }]}>
              {dateText} {item.status === 'COMPLETED' ? '방문함' : '방문 예정'}
            </Text>
          </View>
          <View style={[styles.statusBadge, { backgroundColor: statusBg }]}>
            <Text style={[styles.statusText, { color: statusColor }]}>{statusText}</Text>
          </View>
        </View>

        <View style={styles.cardBody}>
          <Image 
            source={{ uri: 'https://via.placeholder.com/150' }} 
            style={styles.cardImage} 
          />
          <View style={styles.cardInfo}>
            <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{storeName}</Text>
            {item.visitorCount && (
              <Text style={styles.amountText}>방문 인원: {item.visitorCount}명</Text>
            )}
          </View>
        </View>

        {/* 하단 버튼 영역 분기 처리 */}
        <View style={{ marginTop: 12 }}>
          
          {/* 1. 예약 취소하기 버튼 (유지) */}
          {(item.status === 'PENDING' || item.status === 'APPROVED' || item.status === 'RESERVED' || item.status === 'CONFIRMED') && (
            <TouchableOpacity style={styles.cancelButton} onPress={(e) => { e.stopPropagation(); handleCancelReservation(rId); }}>
              <Text fontWeight="bold" style={styles.cancelButtonText}>예약 취소하기</Text>
            </TouchableOpacity>
          )}

          {/* 2. 리뷰 작성 / 완료 버튼 (교체된 isReviewCompleted가 여기서 작동합니다!) */}
          {item.status === 'COMPLETED' && (
            isReviewCompleted ? (
              // 🟢 리뷰 작성이 완료되었을 때 (회색 버튼)
              <View style={[styles.reviewButton, { backgroundColor: '#F5F5F5', borderColor: '#EEE' }]}>
                <Text fontWeight="bold" style={[styles.reviewButtonText, { color: '#999' }]}>리뷰 작성 완료</Text>
              </View>
            ) : (
              // 🔵 아직 리뷰를 안 썼을 때 (초록색 버튼)
              <TouchableOpacity 
                style={styles.reviewButton}
                onPress={(e) => {
                  e.stopPropagation(); 
                  router.push(`/review/write?storeId=${item.storeId}&reservationId=${rId}`);
                }}
              >
                <Text fontWeight="bold" style={styles.reviewButtonText}>리뷰 작성하기</Text>
              </TouchableOpacity>
            )
          )}
        </View>
      </TouchableOpacity>
    );
  };

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
          <Text style={styles.emptyText}>예약 내역이 없어요.</Text>
        </View>
      ) : (
        <FlatList
          data={reservationList}
          keyExtractor={(item) => item.visitReservationId?.toString()}
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
    backgroundColor: '#FFF5F5', paddingVertical: 12, 
    borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#FF5252' 
  },
  cancelButtonText: { color: '#FF5252', fontSize: 14 },
  reviewButton: { 
    backgroundColor: '#F0F9F4', paddingVertical: 12, 
    borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#00A859' 
  },
  reviewButtonText: { color: '#00A859', fontSize: 14 }
});