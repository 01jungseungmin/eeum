import React from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function CheckoutScreen() {
  const router = useRouter();

  const handlePayment = () => {
    Alert.alert("결제 완료", "주문이 성공적으로 접수되었습니다!", [
      { text: "확인", onPress: () => router.push('/') } // 홈으로 이동
    ]);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>주문 / 결제</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 주문 상품 요약 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 상품</Text>
          <Text style={styles.summaryText}>맛있는 반찬가게 - 깍두기 500g 외 1건</Text>
        </View>
        <View style={styles.divider} />

        {/* 배송지 정보 (UI 껍데기) */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>배송지 정보</Text>
          <View style={styles.inputBox}>
            <Text style={styles.inputLabel}>받으시는 분</Text>
            <Text style={styles.inputValue}>정원님</Text>
          </View>
          <View style={styles.inputBox}>
            <Text style={styles.inputLabel}>연락처</Text>
            <Text style={styles.inputValue}>010-1234-5678</Text>
          </View>
          <View style={styles.inputBox}>
            <Text style={styles.inputLabel}>배송 주소</Text>
            <Text style={styles.inputValue}>서울특별시 종로구 청운동 123-4 (이음아파트 101동)</Text>
          </View>
        </View>
        <View style={styles.divider} />

        {/* 결제 금액 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>결제 상세</Text>
          <View style={styles.priceRow}>
            <Text style={styles.priceLabel}>주문 금액</Text>
            <Text style={styles.priceValue}>19,000원</Text>
          </View>
          <View style={styles.priceRow}>
            <Text style={styles.priceLabel}>배달비</Text>
            <Text style={styles.priceValue}>+ 3,000원</Text>
          </View>
          <View style={[styles.priceRow, { marginTop: 15, paddingTop: 15, borderTopWidth: 1, borderColor: '#eee' }]}>
            <Text fontWeight="bold" style={styles.totalPriceLabel}>총 결제 금액</Text>
            <Text fontWeight="bold" style={styles.totalPriceValue}>22,000원</Text>
          </View>
        </View>
      </ScrollView>

      {/* 최종 결제 버튼 */}
      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.payBtn} onPress={handlePayment}>
          <Text fontWeight="bold" style={styles.payBtnText}>22,000원 결제하기</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#eee' },
  backButton: { padding: 5, marginLeft: -5 },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  section: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 15 },
  summaryText: { fontSize: 15, color: '#555' },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  inputBox: { marginBottom: 15 },
  inputLabel: { fontSize: 13, color: '#888', marginBottom: 5 },
  inputValue: { fontSize: 15, color: '#333', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#eee' },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  priceLabel: { fontSize: 15, color: '#666' },
  priceValue: { fontSize: 15, color: '#333' },
  totalPriceLabel: { fontSize: 16, color: '#333' },
  totalPriceValue: { fontSize: 20, color: '#00A859' },
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff' },
  payBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  payBtnText: { color: '#fff', fontSize: 16 }
});