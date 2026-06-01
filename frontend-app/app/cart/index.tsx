import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';

import { cartApi } from '../../api/cart';

export default function CartScreen() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(false);
  const [cartData, setCartData] = useState<any>(null);

  // ✨ 화면에 들어올 때마다 최신 장바구니 데이터를 불러옵니다.
  useFocusEffect(
    useCallback(() => {
      loadCartData();
    }, [])
  );

  const loadCartData = async () => {
    setIsLoading(true);
    try {
      const data = await cartApi.getCart();
      setCartData(data); // 데이터가 없거나 비어있으면 null 혹은 빈 배열 처리
    } catch (e) {
      console.log('장바구니 조회 실패:', e);
      setCartData(null);
    } finally {
      setIsLoading(false);
    }
  };

  // 수량 조절 (+, -)
  const handleUpdateQuantity = async (cartItemId: number, currentQty: number, delta: number) => {
    const newQty = currentQty + delta;
    if (newQty < 1) return; // 1개 미만으로는 줄일 수 없음

    try {
      await cartApi.updateQuantity(cartItemId, newQty);
      loadCartData(); // 수량 변경 성공 후 화면 갱신
    } catch (e) {
      Alert.alert('오류', '수량 변경에 실패했습니다.');
    }
  };

  // 개별 상품 삭제
  const handleRemoveItem = (cartItemId: number) => {
    Alert.alert('삭제', '이 상품을 장바구니에서 뺄까요?', [
      { text: '취소', style: 'cancel' },
      { 
        text: '삭제', 
        style: 'destructive',
        onPress: async () => {
          try {
            await cartApi.removeCartItem(cartItemId);
            loadCartData();
          } catch (e) {
            Alert.alert('오류', '상품 삭제에 실패했습니다.');
          }
        }
      }
    ]);
  };

  // 전체 비우기
  const handleClearCart = () => {
    Alert.alert('전체 삭제', '장바구니를 모두 비우시겠습니까?', [
      { text: '취소', style: 'cancel' },
      { 
        text: '비우기', 
        style: 'destructive',
        onPress: async () => {
          try {
            await cartApi.clearCart();
            setCartData(null); // 화면 즉시 비우기
          } catch (e) {
            Alert.alert('오류', '장바구니 비우기에 실패했습니다.');
          }
        }
      }
    ]);
  };

  // 🚧 결제 페이지로 이동
  const handleCheckout = () => {
    if (!cartData || !cartData.items || cartData.items.length === 0) {
      Alert.alert('알림', '장바구니가 비어있습니다.');
      return;
    }
    
    // 결제 페이지로 넘기기 (필요시 라우터 파라미터로 cartId 등을 넘길 수 있습니다)
    router.push('/order/checkout');
  };

  const isEmpty = !cartData || !cartData.items || cartData.items.length === 0;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>장바구니</Text>
        <TouchableOpacity onPress={handleClearCart}>
          <Text style={styles.clearText}>전체삭제</Text>
        </TouchableOpacity>
      </View>

      {isLoading && !cartData ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : isEmpty ? (
        <View style={styles.centerBox}>
          <Ionicons name="cart-outline" size={60} color="#CCC" style={{ marginBottom: 15 }} />
          <Text style={styles.emptyText}>장바구니에 담긴 상품이 없습니다.</Text>
          <TouchableOpacity style={styles.goShopBtn} onPress={() => router.push('/')}>
            <Text fontWeight="bold" style={styles.goShopBtnText}>쇼핑하러 가기</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <>
          <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 30 }}>
            <View style={styles.storeHeader}>
              <Text fontWeight="bold" style={styles.storeName}>{cartData.storeName}</Text>
            </View>
            
            {/* 장바구니 아이템 리스트 */}
            {cartData.items.map((item: any) => (
              <View key={item.cartItemId} style={styles.cartItem}>
                <Image 
                  source={{ uri: item.thumbnailUrl || 'https://via.placeholder.com/150/E8F5E9/00A859?text=Item' }} 
                  style={styles.itemImage} 
                />
                
                <View style={styles.itemInfo}>
                  <View style={styles.itemTitleRow}>
                    <Text fontWeight="bold" style={styles.itemName} numberOfLines={1}>{item.productName}</Text>
                    <TouchableOpacity onPress={() => handleRemoveItem(item.cartItemId)}>
                      <Ionicons name="close" size={20} color="#999" />
                    </TouchableOpacity>
                  </View>
                  
                  {item.selectedOptionsText && (
                    <Text style={styles.itemOption}>{item.selectedOptionsText}</Text>
                  )}
                  
                  <Text fontWeight="bold" style={styles.itemPrice}>{item.totalPrice.toLocaleString()}원</Text>
                  
                  {/* 수량 조절 컨트롤러 */}
                  <View style={styles.qtyContainer}>
                    <TouchableOpacity 
                      style={styles.qtyBtn} 
                      onPress={() => handleUpdateQuantity(item.cartItemId, item.quantity, -1)}
                    >
                      <Ionicons name="remove" size={16} color={item.quantity > 1 ? "#333" : "#CCC"} />
                    </TouchableOpacity>
                    <Text style={styles.qtyText}>{item.quantity}</Text>
                    <TouchableOpacity 
                      style={styles.qtyBtn} 
                      onPress={() => handleUpdateQuantity(item.cartItemId, item.quantity, 1)}
                    >
                      <Ionicons name="add" size={16} color="#333" />
                    </TouchableOpacity>
                  </View>
                </View>
              </View>
            ))}

            <View style={styles.divider} />

            {/* 결제 요약 */}
            <View style={styles.summarySection}>
              <View style={styles.summaryRow}>
                <Text style={styles.summaryLabel}>상품 금액</Text>
                <Text style={styles.summaryValue}>{cartData.totalPrice.toLocaleString()}원</Text>
              </View>
              <View style={styles.summaryRow}>
                <Text style={styles.summaryLabel}>배달비 (예상)</Text>
                <Text style={styles.summaryValue}>+ 3,000원</Text>
              </View>
              <View style={[styles.summaryRow, styles.totalRow]}>
                <Text fontWeight="bold" style={styles.totalLabel}>총 결제 예상 금액</Text>
                <Text fontWeight="bold" style={styles.totalValue}>{(cartData.totalPrice + 3000).toLocaleString()}원</Text>
              </View>
            </View>
          </ScrollView>

          {/* 하단 결제 버튼 */}
          <View style={styles.bottomBar}>
            <TouchableOpacity style={styles.checkoutBtn} onPress={handleCheckout}>
              <Text fontWeight="bold" style={styles.checkoutBtnText}>
                {(cartData.totalPrice + 3000).toLocaleString()}원 결제하기
              </Text>
            </TouchableOpacity>
          </View>
        </>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#eee' },
  headerTitle: { fontSize: 18, color: '#333' },
  clearText: { fontSize: 14, color: '#888' },
  centerBox: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyText: { fontSize: 16, color: '#888', marginBottom: 20 },
  goShopBtn: { paddingHorizontal: 20, paddingVertical: 12, borderRadius: 8, borderWidth: 1, borderColor: '#00A859' },
  goShopBtnText: { color: '#00A859', fontSize: 15 },
  storeHeader: { padding: 20, borderBottomWidth: 1, borderBottomColor: '#F8F8F8' },
  storeName: { fontSize: 18, color: '#333' },
  cartItem: { flexDirection: 'row', padding: 20, borderBottomWidth: 1, borderBottomColor: '#F8F8F8' },
  itemImage: { width: 80, height: 80, borderRadius: 8, marginRight: 15 },
  itemInfo: { flex: 1, justifyContent: 'center' },
  itemTitleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  itemName: { fontSize: 16, color: '#333', flex: 1 },
  itemOption: { fontSize: 13, color: '#888', marginBottom: 8 },
  itemPrice: { fontSize: 16, color: '#333', marginBottom: 10 },
  qtyContainer: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 6 },
  qtyBtn: { padding: 8 },
  qtyText: { paddingHorizontal: 12, fontSize: 15, fontWeight: '500' },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  summarySection: { padding: 20 },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
  summaryLabel: { fontSize: 15, color: '#666' },
  summaryValue: { fontSize: 15, color: '#333' },
  totalRow: { marginTop: 10, paddingTop: 15, borderTopWidth: 1, borderTopColor: '#EEE' },
  totalLabel: { fontSize: 16, color: '#333' },
  totalValue: { fontSize: 20, color: '#00A859' },
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff' },
  checkoutBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  checkoutBtnText: { color: '#fff', fontSize: 16 }
});