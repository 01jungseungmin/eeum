import React from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function OrderDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();

  /* 🚧 [백엔드 API 연동 시 주석 해제]
  useEffect(() => {
    const fetchOrderDetail = async () => {
      const res = await orderApi.getOrderDetail(id);
      setOrder(res.data);
    };
    fetchOrderDetail();
  }, [id]);
  */

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#fff" /></TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>주문 상세</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 상점 정보 */}
        <View style={styles.section}>
          <View style={styles.orderHeader}>
             <Text style={styles.orderId}>주문번호: EE-20260428-001</Text>
             <Text style={styles.orderDate}>2026.04.28 15:40</Text>
          </View>
          <View style={styles.storeCard}>
            <View style={styles.storeAvatar}><Text style={{color:'#fff'}}>M</Text></View>
            <View style={{flex:1}}>
              <Text fontWeight="bold" style={styles.storeName}>맛있는 반찬가게</Text>
              <Text style={styles.storeInfo}>음식점 | 02-1234-5678</Text>
              <Text style={styles.storeInfo}>월~토 09:00 - 19:00</Text>
            </View>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 주문한 상품 목록 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 메뉴</Text>
          <View style={styles.menuItem}>
            <Text style={styles.menuName}>깍두기 500g (2개)</Text>
            <Text style={styles.menuPrice}>19,000원</Text>
          </View>
          <View style={styles.menuItem}>
            <Text style={styles.menuName}>배달비</Text>
            <Text style={styles.menuPrice}>3,000원</Text>
          </View>
          <View style={styles.menuItem}>
            <Text style={styles.menuName}>할인금액</Text>
            <Text style={[styles.menuPrice, {color:'#FF5252'}]}>-4,000원</Text>
          </View>
          <View style={[styles.menuItem, {marginTop: 10, paddingTop: 10, borderTopWidth:1, borderTopColor:'#eee'}]}>
            <Text fontWeight="bold" style={styles.totalLabel}>총 결제금액</Text>
            <Text fontWeight="bold" style={styles.totalValue}>18,000원</Text>
          </View>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.outlineBtn}><Text fontWeight="bold" style={styles.outlineBtnText}>전화하기</Text></TouchableOpacity>
        <TouchableOpacity style={styles.solidBtn}><Text fontWeight="bold" style={styles.solidBtnText}>리뷰 작성</Text></TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#00A859', paddingHorizontal: 20, paddingVertical: 15 },
  headerTitle: { fontSize: 18, color: '#fff' },
  section: { padding: 20 },
  orderHeader: { marginBottom: 15 },
  orderId: { fontSize: 14, color: '#333', fontWeight: 'bold' },
  orderDate: { fontSize: 12, color: '#999', marginTop: 2 },
  storeCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#F8F9FA', padding: 15, borderRadius: 12 },
  storeAvatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#00A859', justifyContent: 'center', alignItems: 'center', marginRight: 15 },
  storeName: { fontSize: 16, marginBottom: 2 },
  storeInfo: { fontSize: 13, color: '#888' },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 15 },
  menuItem: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  menuName: { fontSize: 15, color: '#666' },
  menuPrice: { fontSize: 15, color: '#333' },
  totalLabel: { fontSize: 16 },
  totalValue: { fontSize: 18, color: '#00A859' },
  footer: { flexDirection: 'row', padding: 20, gap: 10, borderTopWidth: 1, borderTopColor: '#eee' },
  outlineBtn: { flex: 1, paddingVertical: 15, borderRadius: 8, borderWidth: 1, borderColor: '#00A859', alignItems: 'center' },
  outlineBtnText: { color: '#00A859' },
  solidBtn: { flex: 1, paddingVertical: 15, borderRadius: 8, backgroundColor: '#00A859', alignItems: 'center' },
  solidBtnText: { color: '#fff' }
});