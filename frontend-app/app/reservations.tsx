import React, { useState, useEffect } from 'react';
import { 
  StyleSheet, View, FlatList, Image, 
  TouchableOpacity, ActivityIndicator, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../components/CustomText';

// ✨ API 임포트 (경로는 프로젝트 환경에 맞게 수정하세요)
import { reservationApi } from '@/api/reservation'; 

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
      
      // ✨ 1. 진짜 예약 내역 API 호출
      const res = await reservationApi.getMyVisitReservations();
      
      // 서버 응답 구조에 맞게 배열 추출
      const realData = res?.content || res?.data || res || [];
      
      // ✨ 2. 데이터 구조 파악을 위한 로그 (이름, 날짜 등의 Key값 확인용)
      console.log("🔥 실제 예약 내역 데이터:", JSON.stringify(realData[0], null, 2));

      setReservationList(realData);

    } catch (error) {
      console.error('예약 내역 로딩 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // ✨ 3. 실제 예약 취소 API 연동
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
              // 백엔드 취소 API 호출
              await reservationApi.cancelVisitReservation(reservationId);
              Alert.alert('알림', '예약이 정상적으로 취소되었습니다.');
              
              // 취소 후 목록을 다시 불러와서 화면을 갱신합니다.
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
    // ✨ 실제 데이터의 Key 이름으로 변경!
    const rId = item.visitReservationId; 
    const storeName = item.storeName;
    
    // 시간 뒤의 초(00) 자르기 (예: "11:30:00" -> "11:30")
    const timeText = item.visitTime ? item.visitTime.substring(0, 5) : '';
    const dateText = `${item.visitDate} ${timeText}`;

    // ✨ 예약 상태(status)에 따른 뱃지 스타일 분기 처리
    let statusText = '예약 대기';
    let statusColor = '#FF9800'; // 주황색 (대기중)
    let statusBg = '#FFF3E0';

    if (item.status === 'APPROVED' || item.status === 'RESERVED' || item.status === 'CONFIRMED') {
      statusText = '예약 확정';
      statusColor = '#2196F3'; // 파란색 (확정)
      statusBg = '#E3F2FD';
    } else if (item.status === 'CANCELLED' || item.status === 'REJECTED') {
      statusText = item.status === 'CANCELLED' ? '예약 취소' : '예약 거절';
      statusColor = '#FF5252'; // 빨간색 (취소/거절)
      statusBg = '#FFEBEE';
    }

    return (
      <TouchableOpacity 
        style={styles.cardContainer}
        onPress={() => router.push(`/restaurant/reservation-detail?id=${rId}` as any)}
      >
        <View style={styles.cardHeader}>
          <View style={styles.headerLeft}>
            <Ionicons name="calendar-outline" size={14} color={statusColor} style={{ marginRight: 4 }} />
            <Text style={[styles.dateText, { color: statusColor }]}>{dateText} 방문 예정</Text>
          </View>
          <View style={[styles.statusBadge, { backgroundColor: statusBg }]}>
            <Text style={[styles.statusText, { color: statusColor }]}>{statusText}</Text>
          </View>
        </View>

        <View style={styles.cardBody}>
          {/* 예약 데이터에 이미지가 없으므로 기본 이미지 사용 */}
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

        {/* 취소나 거절 상태가 아닐 때만 취소 버튼 노출 */}
        {item.status !== 'CANCELLED' && item.status !== 'REJECTED' && (
          <TouchableOpacity 
            style={styles.cancelButton}
            onPress={() => handleCancelReservation(rId)}
          >
            <Text fontWeight="bold" style={styles.cancelButtonText}>예약 취소하기</Text>
          </TouchableOpacity>
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
    marginTop: 12, backgroundColor: '#FFF5F5', paddingVertical: 12, 
    borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#FF5252' 
  },
  cancelButtonText: { color: '#FF5252', fontSize: 14 }
});