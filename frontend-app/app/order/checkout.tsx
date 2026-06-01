import React, { useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import IMP from 'iamport-react-native'; 

import { orderApi } from '../../api/order'; // 방금 만든 결제 API

export default function CheckoutScreen() {
  const router = useRouter();
  
  // 💡 포트원 결제창을 띄울지 말지 결정하는 상태
  const [isPaymentVisible, setIsPaymentVisible] = useState(false);

  // [결제하기] 버튼을 눌렀을 때 실행되는 함수
  const handlePayment = async () => {
    // 1. 백엔드에 먼저 '주문서'를 생성해서 가짜 주문번호(merchant_uid)를 받아와야 합니다.
    // (이 과정은 파트너님과 협의된 API 흐름에 따라 다를 수 있습니다.)
    
    // 2. 주문번호가 준비되면 포트원 결제창을 켭니다.
    setIsPaymentVisible(true);
  };

  // 💡 포트원 결제가 끝난 직후 포트원이 실행시켜 주는 콜백 함수입니다.
  const paymentCallback = async (response: any) => {
    // 결제창을 다시 끕니다.
    setIsPaymentVisible(false);

    // 포트원이 준 결과값 (영수증 번호 등)
    const { success, imp_uid, merchant_uid, error_msg } = response;

    if (success) {
      try {
        // ✨ [가장 중요!] 포트원이 준 영수증 번호를 우리 백엔드로 보내서 '진짜 결제 맞는지' 검증합니다.
        await orderApi.verifyPayment(imp_uid, merchant_uid);
        
        Alert.alert("결제 성공", "주문이 완료되었습니다!", [
          { text: "확인", onPress: () => router.push('/order/complete') }
        ]);
      } catch (e) {
        Alert.alert("결제 검증 실패", "결제는 되었으나 서버 검증에 실패했습니다. 고객센터에 문의해주세요.");
      }
    } else {
      Alert.alert("결제 실패", `결제에 실패했습니다.\n사유: ${error_msg}`);
    }
  };


  if (isPaymentVisible) {
    return (
      <IMP.Payment
        userCode={'imp00000000'} // 포트원(아임포트) 관리자 페이지에서 발급받은 프론트엔드용 식별코드
        loading={<View><Text>결제창을 불러오는 중입니다...</Text></View>}
        data={{
          pg: 'kakaopay', // 결제 방식 (카카오페이, 토스 등)
          pay_method: 'card',
          name: '맛있는 반찬가게 - 깍두기 외 1건',
          merchant_uid: `mid_${new Date().getTime()}`, // 고유 주문번호 (보통 백엔드가 생성해 줌)
          amount: 22000,
          buyer_name: '정원',
          buyer_tel: '010-1234-5678',
          buyer_email: 'test@eeum.com', 
          escrow: false,
          app_scheme: 'eeum',
        }}
        callback={paymentCallback} // 결제 완료 후 실행될 함수 연결
      />
    );
  }

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
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 상품</Text>
          <Text style={styles.summaryText}>맛있는 반찬가게 - 깍두기 500g 외 1건</Text>
        </View>
        <View style={styles.divider} />

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
        </View>
        <View style={styles.divider} />

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