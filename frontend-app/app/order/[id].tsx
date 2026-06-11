import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

// ✨ 주문 API import (실제 경로에 맞게 수정하세요)
import { orderApi } from '../../api/order'; 

export default function OrderDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();

  // ✨ 1. id가 배열일 가능성을 차단하고 안전하게 숫자로 변환합니다.
  const orderIdNum = typeof id === 'string' ? Number(id) : 0;

  const [order, setOrder] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchOrderDetail = async () => {
      try {
        setIsLoading(true);
        
        // 🚨 2. 기존의 'id' 대신 타입이 확실한 'orderIdNum'을 전달합니다!
        const res = await orderApi.getOrderDetail(orderIdNum);
        setOrder(res.data);
      } catch (error) {
        console.error("주문 상세 로딩 에러:", error);
        Alert.alert("오류", "주문 상세 정보를 불러오지 못했습니다.");
        router.back();
      } finally {
        setIsLoading(false);
      }
    };
    // 🚨 3. 조건문도 orderIdNum이 유효할 때(0이 아닐 때)만 돌도록 변경하면 더 안전합니다.
    if (orderIdNum) fetchOrderDetail();
  }, [orderIdNum]);

  if (isLoading) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  if (!order) return null;

  // 결제일로부터 7일이 지났는지 계산.
  const orderDate = new Date(order.modified_at || order.paid_at);
  const now = new Date();
  // 밀리초 단위 차이를 일(day) 단위로 변환
  const diffTime = now.getTime() - orderDate.getTime();
  const diffDays = diffTime / (1000 * 60 * 60 * 24);
  const isWithin7Days = diffDays <= 7;

  const isCompleted = order.status === 'PAID' || order.status === 'COMPLETED';

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
             <Text style={styles.orderId}>주문번호: {order.orderNumber || `EE-2026-${order.orderId}`}</Text>
             <Text style={styles.orderDate}>{order.createdAt?.replace('T', ' ') || '2026.04.28 15:40'}</Text>
          </View>
          <View style={styles.storeCard}>
            <View style={styles.storeAvatar}><Text style={{color:'#fff'}}>{order.storeName?.charAt(0) || 'M'}</Text></View>
            <View style={{flex:1}}>
              <Text fontWeight="bold" style={styles.storeName}>{order.storeName || '상점 이름'}</Text>
              <Text style={styles.storeInfo}>{order.categoryName || '음식점'} | {order.storePhone || '전화번호'}</Text>
            </View>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 주문한 상품 목록 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 메뉴</Text>
          
          {/* 실제 주문 항목 매핑 */}
          {order.orderItems?.map((item: any, index: number) => (
            <View key={index} style={styles.menuItem}>
              <Text style={styles.menuName}>{item.productName} ({item.quantity}개)</Text>
              <Text style={styles.menuPrice}>{item.price?.toLocaleString()}원</Text>
            </View>
          ))}
          
          <View style={styles.menuItem}>
            <Text style={styles.menuName}>배달/포장비</Text>
            <Text style={styles.menuPrice}>{order.deliveryFee?.toLocaleString() || 0}원</Text>
          </View>
          
          <View style={[styles.menuItem, {marginTop: 10, paddingTop: 10, borderTopWidth:1, borderTopColor:'#eee'}]}>
            <Text fontWeight="bold" style={styles.totalLabel}>총 결제금액</Text>
            <Text fontWeight="bold" style={styles.totalValue}>{order.totalAmount?.toLocaleString()}원</Text>
          </View>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.outlineBtn}>
          <Text fontWeight="bold" style={styles.outlineBtnText}>전화하기</Text>
        </TouchableOpacity>
        
        {/* 7일 이내이고, 결제가 완료된 상태일 때만 버튼 노출 */}
        {/* 리뷰 작성 버튼: storeId와 orderId를 완벽하게 넘겨줌 */}
        {isCompleted && isWithin7Days ? (
          <TouchableOpacity 
            style={styles.solidBtn}
            onPress={() => router.push({
              pathname: '/review/write' as any, 
              params: { 
                orderId: order.orderId,
                storeId: order.storeId,
                storeName: order.storeName 
              }
            })}
          >
            <Text fontWeight="bold" style={styles.solidBtnText}>리뷰 작성</Text>
          </TouchableOpacity>
        ) : (
          // 7일이 지났거나 권한이 없으면 회색 비활성화 버튼을 보여줍니다
          <View style={[styles.solidBtn, { backgroundColor: '#CCC' }]}>
            <Text fontWeight="bold" style={styles.solidBtnText}>리뷰 기간 만료</Text>
          </View>
        )}
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