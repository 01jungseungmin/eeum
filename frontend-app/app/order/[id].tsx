import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

// ✨ 주문 API import
import { orderApi } from '../../api/order'; 

export default function OrderDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();

  // id가 배열일 가능성을 차단하고 안전하게 숫자로 변환합니다.
  const orderIdNum = typeof id === 'string' ? Number(id) : 0;

  const [order, setOrder] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchOrderDetail = async () => {
      try {
        setIsLoading(true);
        
        // 확실한 타입의 orderIdNum을 전달하여 상세 내역 조회
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

  // 결제일로부터 7일이 지났는지 계산
  const orderDate = new Date(order.modifiedAt || order.paidAt || order.createdAt);
  const now = new Date();
  const diffTime = now.getTime() - orderDate.getTime();
  const diffDays = diffTime / (1000 * 60 * 60 * 24);
  const isWithin7Days = diffDays <= 7;

  const isCompleted = order.status === 'PAID' || order.status === 'COMPLETED';

  return (
    <SafeAreaView style={styles.container}>
      {/* 헤더 상단바 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color="#fff" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>주문 상세</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 상점 정보 및 주문 번호 섹션 */}
        <View style={styles.section}>
          <View style={styles.orderHeader}>
             <Text style={styles.orderId}>주문번호: {order.orderNumber || `EE-2026-${order.orderId}`}</Text>
             <Text style={styles.orderDate}>{order.createdAt?.replace('T', ' ').substring(0, 16) || ''}</Text>
          </View>
          <View style={styles.storeCard}>
            <View style={styles.storeAvatar}>
              <Text style={{color:'#fff'}}>{order.storeName?.charAt(0) || 'M'}</Text>
            </View>
            <View style={{flex:1}}>
              <Text fontWeight="bold" style={styles.storeName}>{order.storeName || '상점 이름'}</Text>
              <Text style={styles.storeInfo}>{order.categoryName || '이음 상점'} | {order.storePhone || '전화번호 없음'}</Text>
            </View>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 주문한 상품 목록 섹션 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 메뉴</Text>
          
          {/* 실제 주문 항목 매핑 (필드명 방어코드 적용) */}
          {order.orderItems?.map((item: any, index: number) => (
            <View key={index} style={styles.menuItem}>
              <Text style={styles.menuName}>
                {item.productName || item.name} ({item.quantity || 1}개)
              </Text>
              {/* 개당 가격 * 수량으로 정확한 서브 토탈 표기 */}
              <Text style={styles.menuPrice}>
                {((item.price || 0) * (item.quantity || 1)).toLocaleString()}원
              </Text>
            </View>
          ))}
          
          {/* ❌ 기존에 존재하던 배달/포장비 View 블록 완전 삭제 완료 */}
          
          {/* ✨ 총 결제금액 표기 수정 (totalAmount -> totalPrice 명세 동기화) */}
          <View style={[styles.menuItem, {marginTop: 15, paddingTop: 15, borderTopWidth: 1, borderTopColor: '#eee'}]}>
            <Text fontWeight="bold" style={styles.totalLabel}>총 결제금액</Text>
            <Text fontWeight="bold" style={styles.totalValue}>
              {(order.totalPrice || order.totalAmount || 0).toLocaleString()}원
            </Text>
          </View>
        </View>
      </ScrollView>

      {/* 하단 고정 버튼 영역 */}
      <View style={styles.footer}>
        <TouchableOpacity style={styles.outlineBtn}>
          <Text fontWeight="bold" style={styles.outlineBtnText}>전화하기</Text>
        </TouchableOpacity>
        
        {/* 7일 이내 완료된 주문인 경우에만 리뷰 작성 허용 */}
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
  storeName: { fontSize: 16, marginBottom: 2, color: '#333' },
  storeInfo: { fontSize: 13, color: '#888' },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  sectionTitle: { fontSize: 16, color: '#333', marginBottom: 15 },
  menuItem: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12, alignItems: 'center' },
  menuName: { fontSize: 15, color: '#555' },
  menuPrice: { fontSize: 15, color: '#333', fontWeight: '500' },
  totalLabel: { fontSize: 16, color: '#333' },
  totalValue: { fontSize: 18, color: '#00A859' },
  footer: { flexDirection: 'row', padding: 20, gap: 10, borderTopWidth: 1, borderTopColor: '#eee', backgroundColor: '#fff' },
  outlineBtn: { flex: 1, paddingVertical: 15, borderRadius: 8, borderWidth: 1, borderColor: '#00A859', alignItems: 'center' },
  outlineBtnText: { color: '#00A859' },
  solidBtn: { flex: 1, paddingVertical: 15, borderRadius: 8, backgroundColor: '#00A859', alignItems: 'center' },
  solidBtnText: { color: '#fff' }
});