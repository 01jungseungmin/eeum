import React, { useEffect, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { reservationApi } from '../../api/reservation';

export default function ReservationDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();
  
  const [detail, setDetail] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        // 1. 백엔드에 예약 상세 정보 요청
        const data = await reservationApi.getVisitReservationDetail(Number(id));
        setDetail(data);
      } catch (error) {
        Alert.alert('오류', '예약 정보를 불러오는데 실패했습니다.');
        router.back();
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchDetail();
    } else {
      setLoading(false);
    }
  }, [id]);

  // 백엔드에서 주는 영문 상태(PENDING 등)를 한글로 예쁘게 변환해주는 함수
  const getStatusDisplay = (status: string) => {
    switch (status) {
      case 'PENDING': return { text: '예약 대기 (사장님 확인 중)', color: '#F39C12' };
      case 'APPROVED': return { text: '예약 확정 완료', color: '#00A859' };
      case 'REJECTED': return { text: '예약 거절됨', color: '#E74C3C' };
      case 'CANCELLED': return { text: '예약 취소됨', color: '#95A5A6' };
      default: return { text: '상태 알 수 없음', color: '#333' };
    }
  };

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#00A859" />
        <Text style={{ marginTop: 10 }}>예약 정보를 불러오는 중...</Text>
      </View>
    );
  }

  if (!detail) return null;

  const statusInfo = getStatusDisplay(detail.status);

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ padding: 5 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>예약 상세 내역</Text>
        <View style={{ width: 34 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        
        {/* 1. 예약 상태 카드 */}
        <View style={styles.card}>
          <View style={styles.statusBadge}>
            <Ionicons name="information-circle" size={20} color={statusInfo.color} />
            <Text fontWeight="bold" style={[styles.statusText, { color: statusInfo.color }]}>
              {statusInfo.text}
            </Text>
          </View>
          {detail.rejectReason && (
            <Text style={styles.rejectReason}>거절 사유: {detail.rejectReason}</Text>
          )}
        </View>

        {/* 2. 매장 정보 */}
        <View style={styles.card}>
          <Text fontWeight="bold" style={styles.cardTitle}>방문 매장</Text>
          <View style={styles.row}>
            <Text style={styles.label}>매장명</Text>
            <Text fontWeight="bold" style={styles.value}>{detail.storeName}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>주소</Text>
            <Text style={styles.value}>{detail.storeAddress}</Text>
          </View>
        </View>

        {/* 3. 예약 상세 정보 */}
        <View style={styles.card}>
          <Text fontWeight="bold" style={styles.cardTitle}>예약 정보</Text>
          <View style={styles.row}>
            <Text style={styles.label}>예약 번호</Text>
            <Text style={styles.value}>EX-{detail.visitDate?.replace(/-/g, '')}-{String(detail.visitReservationId).padStart(4, '0')}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>방문 일정</Text>
            <Text fontWeight="bold" style={styles.value}>{detail.visitDate}  {detail.visitTime?.substring(0, 5)}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>방문 인원</Text>
            <Text style={styles.value}>{detail.visitorCount}명</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>요청 사항</Text>
            <Text style={styles.value}>{detail.requestMessage || '없음'}</Text>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F4F5F7' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#fff', borderBottomWidth: 1, borderBottomColor: '#E0E0E0' },
  headerTitle: { fontSize: 16, color: '#333' },
  scrollContent: { padding: 15, paddingBottom: 50 },
  card: { backgroundColor: '#fff', borderRadius: 10, padding: 20, marginBottom: 15, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  statusBadge: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#F9FAFB', padding: 15, borderRadius: 8 },
  statusText: { fontSize: 16, marginLeft: 8 },
  rejectReason: { marginTop: 10, color: '#E74C3C', fontSize: 13, paddingHorizontal: 5 },
  cardTitle: { fontSize: 16, color: '#333', marginBottom: 15, paddingBottom: 10, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
  label: { fontSize: 14, color: '#888', flex: 1 },
  value: { fontSize: 14, color: '#333', flex: 2, textAlign: 'right' }
});