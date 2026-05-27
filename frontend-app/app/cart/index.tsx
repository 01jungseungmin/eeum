import React, { useState } from 'react';
import { View, Text, StyleSheet, Image, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

// 💡 테스트용 장바구니 더미 데이터
const INITIAL_CART_ITEMS = [
  {
    id: 'c1',
    storeName: '맛있는 반찬가게',
    name: '깍두기 500g',
    option: '포장 방식: 진공 포장',
    price: 3500,
    quantity: 2,
    imageUrl: 'https://via.placeholder.com/100/E8F5E9/00A859?text=Radish',
    selected: true,
  },
  {
    id: 'c2',
    storeName: '맛있는 반찬가게',
    name: '배추김치 1kg',
    option: '',
    price: 12000,
    quantity: 1,
    imageUrl: 'https://via.placeholder.com/100/E8F5E9/00A859?text=Kimchi',
    selected: true,
  }
];

export default function CartScreen() {
  const router = useRouter();
  
  // 장바구니 상태 관리
  const [cartItems, setCartItems] = useState(INITIAL_CART_ITEMS);

  // 상점 이름
  const storeName = cartItems.length > 0 ? cartItems[0].storeName : '';

  // 전체 선택 여부 확인
  const isAllSelected = cartItems.length > 0 && cartItems.every(item => item.selected);

  // 선택된 상품 총 금액 계산
  const totalAmount = cartItems
    .filter(item => item.selected)
    .reduce((sum, item) => sum + (item.price * item.quantity), 0);
    
  const selectedCount = cartItems.filter(item => item.selected).length;

  // 개별 상품 선택/해제
  const toggleItemSelection = (id: string) => {
    setCartItems(prev => prev.map(item => 
      item.id === id ? { ...item, selected: !item.selected } : item
    ));
  };

  // 전체 선택/해제
  const toggleAllSelection = () => {
    const nextState = !isAllSelected;
    setCartItems(prev => prev.map(item => ({ ...item, selected: nextState })));
  };

  // 수량 변경
  const updateQuantity = (id: string, delta: number) => {
    setCartItems(prev => prev.map(item => {
      if (item.id === id) {
        const newQuantity = item.quantity + delta;
        return { ...item, quantity: newQuantity > 0 ? newQuantity : 1 }; // 최소 1개 유지
      }
      return item;
    }));
  };

  // 상품 삭제
  const removeItem = (id: string) => {
    setCartItems(prev => prev.filter(item => item.id !== id));
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'bottom']}>
      {/* 초록색 헤더 영역 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={24} color="#fff" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>장바구니</Text>
        <View style={{ width: 24 }} />
      </View>

      {/* 장바구니 내용 영역 */}
      {cartItems.length === 0 ? (
        // 🚨 상태 2: 장바구니가 비어있을 때 (추천 상품 링크 삭제됨)
        <View style={styles.emptyContainer}>
          <Ionicons name="cart-outline" size={80} color="#333" style={styles.emptyIcon} />
          <View style={styles.emptyBadge}><Ionicons name="close" size={20} color="#fff" /></View>
          <Text style={styles.emptyText}>장바구니에 담긴 상품이 없습니다.</Text>
        </View>
      ) : (
        // 🛍️ 상태 1: 상품이 담겨있을 때
        <>
          <ScrollView style={styles.cartContainer} showsVerticalScrollIndicator={false}>
            {/* 가게 이름 및 전체 선택 */}
            <View style={styles.storeHeader}>
              <View style={styles.storeHeaderLeft}>
                <TouchableOpacity onPress={toggleAllSelection}>
                  <Ionicons name={isAllSelected ? "checkbox" : "square-outline"} size={22} color={isAllSelected ? "#00A859" : "#ccc"} />
                </TouchableOpacity>
                <Text style={styles.storeName}>{storeName}</Text>
              </View>
              <TouchableOpacity onPress={toggleAllSelection}>
                <Text style={styles.selectAllText}>전체선택</Text>
              </TouchableOpacity>
            </View>

            {/* 상품 리스트 */}
            {cartItems.map((item) => (
              <View key={item.id} style={styles.cartItem}>
                <TouchableOpacity onPress={() => toggleItemSelection(item.id)} style={styles.itemCheckbox}>
                  <Ionicons name={item.selected ? "checkbox" : "square-outline"} size={22} color={item.selected ? "#00A859" : "#ccc"} />
                </TouchableOpacity>

                <Image source={{ uri: item.imageUrl }} style={styles.itemImage} />

                <View style={styles.itemInfo}>
                  <View style={styles.itemTitleRow}>
                    <Text style={styles.itemName} numberOfLines={1}>{item.name}</Text>
                    <TouchableOpacity onPress={() => removeItem(item.id)}>
                      <Ionicons name="close-outline" size={20} color="#999" />
                    </TouchableOpacity>
                  </View>
                  
                  {item.option ? <Text style={styles.itemOption}>{item.option}</Text> : null}
                  <Text style={styles.itemPrice}>{item.price.toLocaleString()}원</Text>
                  
                  <View style={styles.quantityControl}>
                    <TouchableOpacity style={styles.qtyBtn} onPress={() => updateQuantity(item.id, -1)}>
                      <Ionicons name="remove" size={16} color="#666" />
                    </TouchableOpacity>
                    <Text style={styles.qtyText}>{item.quantity}</Text>
                    <TouchableOpacity style={styles.qtyBtn} onPress={() => updateQuantity(item.id, 1)}>
                      <Ionicons name="add" size={16} color="#666" />
                    </TouchableOpacity>
                  </View>
                </View>
              </View>
            ))}
          </ScrollView>

          {/* 하단 결제 금액 바 */}
          <View style={styles.bottomBar}>
            <Text style={styles.summaryLabel}>선택 상품 금액 ({selectedCount}개)</Text>
            <Text style={styles.summaryTotal}>{totalAmount.toLocaleString()}원</Text>
            
            <TouchableOpacity 
              style={[styles.checkoutBtn, selectedCount > 0 ? styles.checkoutBtnActive : styles.checkoutBtnDisabled]}
              disabled={selectedCount === 0}
              onPress={() => router.push('/order/checkout')}
            >
              <Text style={styles.checkoutBtnText}>
                {selectedCount > 0 ? `${totalAmount.toLocaleString()}원 결제하기` : '상품을 선택해주세요'}
              </Text>
            </TouchableOpacity>
          </View>
        </>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: '#F8F9FA' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#00A859', paddingHorizontal: 20, paddingVertical: 15 },
  backButton: { padding: 5, marginLeft: -5 },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#fff' },
  
  // Empty State Styles (추천 상품 링크 관련 속성 삭제)
  emptyContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F8F9FA' },
  emptyIcon: { marginBottom: 10 },
  emptyBadge: { position: 'absolute', top: '40%', right: '38%', backgroundColor: '#333', borderRadius: 10, width: 20, height: 20, justifyContent: 'center', alignItems: 'center' },
  emptyText: { fontSize: 15, color: '#666', marginBottom: 15 },

  // Cart List Styles
  cartContainer: { flex: 1, backgroundColor: '#fff', margin: 10, borderRadius: 8, elevation: 1, shadowColor: '#000', shadowOffset: {width: 0, height: 1}, shadowOpacity: 0.1, shadowRadius: 2 },
  storeHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  storeHeaderLeft: { flexDirection: 'row', alignItems: 'center' },
  storeName: { fontSize: 16, fontWeight: 'bold', color: '#333', marginLeft: 10 },
  selectAllText: { fontSize: 13, color: '#888' },
  
  cartItem: { flexDirection: 'row', padding: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  itemCheckbox: { marginRight: 10, marginTop: 5 },
  itemImage: { width: 70, height: 70, borderRadius: 8, backgroundColor: '#eee' },
  itemInfo: { flex: 1, marginLeft: 12 },
  itemTitleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  itemName: { fontSize: 15, fontWeight: 'bold', color: '#333', flex: 1, marginRight: 10 },
  itemOption: { fontSize: 12, color: '#888', marginTop: 4 },
  itemPrice: { fontSize: 15, fontWeight: 'bold', color: '#00A859', marginTop: 8 },
  
  quantityControl: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', marginTop: 10, borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 20 },
  qtyBtn: { paddingHorizontal: 10, paddingVertical: 4 },
  qtyText: { fontSize: 14, fontWeight: 'bold', marginHorizontal: 8, color: '#333' },

  // Bottom Bar Styles
  bottomBar: { backgroundColor: '#fff', padding: 20, borderTopWidth: 1, borderTopColor: '#EEE' },
  summaryLabel: { fontSize: 13, color: '#888', marginBottom: 4 },
  summaryTotal: { fontSize: 22, fontWeight: 'bold', color: '#333', marginBottom: 15 },
  checkoutBtn: { paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  checkoutBtnActive: { backgroundColor: '#00A859' },
  checkoutBtnDisabled: { backgroundColor: '#D4D4D4' },
  checkoutBtnText: { color: '#fff', fontSize: 16, fontWeight: 'bold' }
});