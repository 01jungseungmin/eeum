import React from 'react';
import { View, StyleSheet, TouchableOpacity, SafeAreaView } from 'react-native';
import { Text } from '../../components/CustomText';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

export default function ReservationSuccessScreen() {
  const router = useRouter();
  const { reservationId, month, date, time } = useLocalSearchParams();

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.replace('/')} style={{ padding: 5 }}>
          <Ionicons name="chevron-back" size={24} color="#fff" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>예약 접수</Text>
        <View style={{ width: 34 }} />
      </View>

      <View style={styles.container}>
        <View style={styles.content}>
          <View style={styles.iconCircle}>
            <Ionicons name="checkmark" size={60} color="#fff" />
          </View>
          
          <Text fontWeight="bold" style={styles.title}>예약이 접수되었습니다.</Text>
          <Text style={styles.subtitle}>
            예약이 확정되면 알림으로 알려드릴게요.
          </Text>
          
          <View style={styles.infoBox}>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>예약 번호</Text>
              <Text style={styles.infoValue}>EX-2026{String(month || '').padStart(2, '0')}{String(date || '').padStart(2, '0')}-{String(reservationId || '').padStart(4, '0')}</Text>
            </View>
            <View style={[styles.infoRow, { marginTop: 10 }]}>
              <Text style={styles.infoLabel}>예약 일정</Text>
              <Text style={styles.infoValue}>{month}월 {date}일 {time}</Text>
            </View>
          </View>
        </View>

        <View style={styles.bottomBar}>
          <TouchableOpacity 
            style={styles.detailBtn} 
            onPress={() => {
              // ✨ 상세 화면으로 이동하면서 예약 번호를 넘겨줍니다.
              router.push({
                pathname: '/restaurant/reservation-detail' as any,
                params: { reservationId }
              });
            }}
          >
            <Text style={styles.detailBtnText}>예약 상세 보기</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: '#00A859' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#00A859' },
  headerTitle: { fontSize: 18, color: '#fff' },
  container: { flex: 1, backgroundColor: '#fff' },
  content: { flex: 1, alignItems: 'center', paddingTop: 60, paddingHorizontal: 20 },
  iconCircle: { width: 100, height: 100, borderRadius: 50, backgroundColor: '#00A859', justifyContent: 'center', alignItems: 'center', marginBottom: 25 },
  title: { fontSize: 20, color: '#333', marginBottom: 12 },
  subtitle: { fontSize: 14, color: '#888', marginBottom: 35 },
  infoBox: { width: '100%', backgroundColor: '#F8F9FA', paddingHorizontal: 20, paddingVertical: 18, borderRadius: 8 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  infoLabel: { fontSize: 14, color: '#666' },
  infoValue: { fontSize: 14, color: '#333' },
  bottomBar: { padding: 20, paddingBottom: 30 },
  detailBtn: { width: '100%', backgroundColor: '#fff', borderWidth: 1, borderColor: '#E0E0E0', paddingVertical: 16, borderRadius: 8, alignItems: 'center', marginBottom: 10 },
  detailBtnText: { color: '#333', fontSize: 16 },
  homeBtn: { width: '100%', backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  homeBtnText: { color: '#fff', fontSize: 16 }
});