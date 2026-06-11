import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { orderApi } from '../../api/order';

export default function OrderDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); // [id].tsx 구조의 id 매개변수 바인딩

  const [isLoading, setIsLoading] = useState(true);
  const [order, setOrder] = useState<any>(null);

  // 스웨거 GET /orders/{orderId} 실시간 연동 처리
  useEffect(() => {
    const fetchOrderDetail = async () => {
      // 1. id가 배열(string[])로 들어오면 첫 번째 값만 쓰고, 아니면 그대로 string으로 안전하게 바인딩합니다.
      const safeId = Array.isArray(id) ? id[0] : id;
      
      if (!safeId) return;
      setIsLoading(true);
      
      try {
        // 2. 이제 안전해진 safeId를 API에 넘겨줍니다! (빨간 줄 완전 소멸)
        const res = await orderApi.getOrderDetail(safeId);
        
        // 백엔드 응답 구조에 맞추어 데이터 맵핑
        setOrder(res.data?.data || res.data || res); 
      } catch (error) {
        console.error("주문 상세 조회 실패:", error);
        Alert.alert("오류", "주문 내역을 불러오는 중 문제가 발생했습니다.");
      } finally {
        setIsLoading(false);
      }
    };
    fetchOrderDetail();
  }, [id]);

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  if (isLoading) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  if (!order) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <Text style={{ color: '#888' }}>주문 내역이 존재하지 않습니다.</Text>
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color="#fff" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>주문 상세</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 기본 헤더 섹션 */}
        <View style={styles.section}>
          <View style={styles.orderHeader}>
             <Text style={styles.orderId}>주문번호: {order.orderNumber}</Text>
             <Text style={styles.orderDate}>{formatDate(order.createdAt)}</Text>
          </View>
          <View style={styles.storeCard}>
            <View style={styles.storeAvatar}>
              <Text style={{ color: '#fff', fontWeight: 'bold' }}>
                {order.storeName ? order.storeName.charAt(0) : 'E'}
              </Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text fontWeight="bold" style={styles.storeName}>{order.storeName}</Text>
              <Text style={styles.storeInfo}>이음 공식 제휴 브랜드 매장</Text>
              <Text style={styles.storeInfo}>픽업 예정: {formatDate(order.pickupScheduledAt)}</Text>
            </View>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 품목 리스트 섹션 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 메뉴</Text>
          
          {/* 스웨거 items 배열을 반복 렌더링하도록 동적 설계 */}
          {order.items && order.items.map((item: any) => (
            <View key={item.orderItemId} style={styles.menuItem}>
              <View style={{ flex: 1, marginRight: 10 }}>
                <Text style={styles.menuName}>{item.productName} ({item.quantity}개)</Text>
                {item.selectedOptionsText ? (
                  <Text style={styles.optionText}>{item.selectedOptionsText}</Text>
                ) : null}
              </View>
              <Text style={styles.menuPrice}>
                {(item.lineTotalPrice || (item.unitPrice * item.quantity)).toLocaleString()}원
              </Text>
            </View>
          ))}

          <View style={[styles.menuItem, { marginTop: 20, paddingTop: 15, borderTopWidth: 1, borderTopColor: '#eee' }]}>
            <Text fontWeight="bold" style={styles.totalLabel}>총 결제금액</Text>
            {/* 백엔드 DB 서버 최종 계산 필드 100% 매핑 */}
            <Text fontWeight="bold" style={styles.totalValue}>
              {order.totalPrice.toLocaleString()}원
            </Text>
          </View>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.outlineBtn} onPress={() => Alert.alert("전화 연결", "매장 번호로 유선 연결을 진행합니다.")}>
          <Text fontWeight="bold" style={styles.outlineBtnText}>전화하기</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.solidBtn} onPress={() => Alert.alert("리뷰 작성", "리뷰 작성 피드로 이동합니다.")}>
          <Text fontWeight="bold" style={styles.solidBtnText}>리뷰 작성</Text>
        </TouchableOpacity>
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
  storeName: { fontSize: 16, marginBottom: 2, fontWeight: 'bold', color: '#333' },
  storeInfo: { fontSize: 13, color: '#888', marginTop: 1 },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 15 },
  menuItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  menuName: { fontSize: 15, color: '#333', fontWeight: '500' },
  optionText: { fontSize: 12, color: '#888', marginTop: 2 },
  menuPrice: { fontSize: 15, color: '#555' },
  totalLabel: { fontSize: 16, color: '#333' },
  totalValue: { fontSize: 20, color: '#00A859' },
  footer: { flexDirection: 'row', padding: 20, gap: 10, borderTopWidth: 1, borderTopColor: '#eee', backgroundColor: '#fff' },
  outlineBtn: { flex: 1, paddingVertical: 15, borderRadius: 8, borderWidth: 1, borderColor: '#00A859', alignItems: 'center' },
  outlineBtnText: { color: '#00A859' },
  solidBtn: { flex: 1, paddingVertical: 15, borderRadius: 8, backgroundColor: '#00A859', alignItems: 'center' },
  solidBtnText: { color: '#fff' }
});