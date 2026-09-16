import React from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router'; 
import { SafeAreaView } from 'react-native-safe-area-context';

export default function OrderCompleteScreen() {
  const router = useRouter();
  
  // checkout.tsx에서 밀어준 진짜 데이터 수신
  const params = useLocalSearchParams();
  const orderId = params.orderId ? String(params.orderId) : '1';
  const orderNumber = params.orderNumber ? String(params.orderNumber) : '미발급';
  const totalPrice = Number(params.totalPrice) || 0;

  // 실시간 결제 일시 산출
  const today = new Date();
  const formattedDate = `${today.getFullYear()}.${String(today.getMonth() + 1).padStart(2, '0')}.${String(today.getDate()).padStart(2, '0')} ${String(today.getHours()).padStart(2, '0')}:${String(today.getMinutes()).padStart(2, '0')}`;

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>주문완료</Text>
      </View>

      <View style={styles.content}>
        <View style={styles.successIcon}>
          <Ionicons name="checkmark-circle" size={100} color="#00A859" />
        </View>
        <Text fontWeight="bold" style={styles.completeTitle}>주문이 완료되었습니다!</Text>
        <Text style={styles.completeSubTitle}>맛있게 준비해서 픽업 대기해 드릴게요.</Text>

        <View style={styles.infoBox}>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>주문상품</Text>
            <Text style={styles.infoValue} numberOfLines={1}>{params.orderName || '장바구니 상품'}</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>주문번호</Text>
            <Text style={styles.infoValue}>{orderNumber}</Text> 
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>결제금액</Text>
            <Text fontWeight="bold" style={[styles.infoValue, { color: '#00A859' }]}>
              {totalPrice.toLocaleString()}원
            </Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>결제일시</Text>
            <Text style={styles.infoValue}>{formattedDate}</Text>
          </View>
        </View>
      </View>

      <View style={styles.footer}>
        {/* 대괄호 라우팅 매핑 주소인 [id].tsx로 진짜 orderId를 실어 전송 */}
        <TouchableOpacity style={styles.outlineBtn} onPress={() => router.push(`/order/${orderId}`)}>
          <Text fontWeight="bold" style={styles.outlineBtnText}>주문 상세 보기</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.solidBtn} onPress={() => router.push('/')}>
          <Text fontWeight="bold" style={styles.solidBtnText}>홈으로</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { backgroundColor: '#00A859', paddingVertical: 15, alignItems: 'center' },
  headerTitle: { fontSize: 18, color: '#fff' },
  content: { flex: 1, justifyContent: 'center', padding: 30 },
  successIcon: { marginBottom: 20, alignItems: 'center' },
  completeTitle: { fontSize: 24, color: '#333', marginBottom: 10, textAlign: 'center' },
  completeSubTitle: { fontSize: 16, color: '#888', marginBottom: 40, textAlign: 'center' },
  infoBox: { width: '100%', backgroundColor: '#F8F9FA', padding: 20, borderRadius: 12 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
  infoLabel: { color: '#888', fontSize: 15 },
  infoValue: { color: '#333', fontWeight: '500', fontSize: 15, flex: 1, textAlign: 'right', marginLeft: 10 },
  footer: { padding: 20, gap: 10 },
  outlineBtn: { paddingVertical: 15, borderRadius: 8, borderWidth: 1, borderColor: '#00A859', alignItems: 'center' },
  outlineBtnText: { color: '#00A859' },
  solidBtn: { paddingVertical: 15, borderRadius: 8, backgroundColor: '#00A859', alignItems: 'center' },
  solidBtnText: { color: '#fff' }
});