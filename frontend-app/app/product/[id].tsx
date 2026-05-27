// app/product/[id].tsx - 완전 수정 버전

import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, Alert, ActivityIndicator, Linking } from 'react-native';
import { Text } from '../../components/CustomText'; 
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

// 💡 더미 데이터 import
import { DUMMY_SHOPS, SHOP_CATEGORIES } from '../../constants/shopDummyData';

const { width } = Dimensions.get('window');

// 리뷰 더미 데이터 (생략)

export default function ProductDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  const [isLiked, setIsLiked] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  
  // 데이터 상태
  const [product, setProduct] = useState<any>(null);
  const [seller, setSeller] = useState<any>(null);

  // 1단계: ID로 더미 데이터에서 상품 및 상점 정보 찾기
  useEffect(() => {
    if (!id) return;
    const productIdNum = Number(id);
    let foundProduct = null;
    let foundSeller = null;

    for (const shop of DUMMY_SHOPS) {
      const prod = shop.products?.find(p => p.id === productIdNum);
      if (prod) {
        foundProduct = prod;
        foundSeller = shop;
        break;
      }
    }
    setProduct(foundProduct);
    setSeller(foundSeller);
  }, [id]);

  // 장바구니 담기 (상점 전용)
  const handleAddToCart = async () => {
    Alert.alert("장바구니", `[${product.name}] 상품을 담았습니다!`, [
      { text: "계속 쇼핑", style: "cancel" },
      { text: "장바구니 가기", onPress: () => router.push('/cart') }
    ]);
  };

  // 바로 구매하기 (상점 전용)
  const handleBuyNow = () => {
    /* 
       🚧 나중에 실제 데이터를 넘길 때는 params를 사용합니다:
       router.push({
         pathname: '/order/checkout',
         params: { productId: product.id, quantity: 1 }
       });
    */
    
    // 지금은 결제 페이지 화면으로 바로 이동!
    router.push('/order/checkout');
  };

  if (isLoading || !product) {
    return <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>;
  }

  // 할인 여부 계산
  const hasEvent = !!product.eventPrice;
  const currentPrice = hasEvent ? product.eventPrice : product.price;

  // 💡 [핵심 비즈니스 로직] 카테고리가 1(음식점), 2(카페)는 '식당'으로 분류
  // 그 외 3(반찬가게), 6(마트) 등은 '상점'으로 분류
  const isRestaurant = seller?.categoryId === 1 || seller?.categoryId === 2;

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 상단 이미지 영역 */}
        <View style={styles.imageContainer}>
          <Image source={{ uri: product.imageUrl }} style={styles.productImage} />
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Ionicons name="chevron-back" size={28} color="#fff" />
          </TouchableOpacity>
        </View>

        {/* 상품 기본 정보 섹션 (피그마 반영) */}
        <View style={styles.infoSection}>
          <View style={styles.titleRow}>
            <Text style={styles.categoryText}>{seller?.name}</Text>
            <TouchableOpacity onPress={() => setIsLiked(!isLiked)}>
              <Ionicons name={isLiked ? "heart" : "heart-outline"} size={24} color={isLiked ? "#FF5252" : "#999"} />
            </TouchableOpacity>
          </View>
          <Text fontWeight="bold" style={styles.productTitle}>{product.name}</Text>
          <View style={styles.priceRow}>
            {hasEvent && <Text style={styles.originalPrice}>{product.price.toLocaleString()}원</Text>}
            <Text fontWeight="bold" style={styles.currentPrice}>{currentPrice.toLocaleString()}원</Text>
          </View>
          <View style={styles.tagRow}>
            {product.type === 'RESERVATION' ? (
                <View style={[styles.tagPill, {backgroundColor: '#E3F2FD'}]}>
                    <Text style={[styles.tagText, {color: '#2196F3'}]}>예약상품</Text>
                </View>
            ) : null}
             <View style={styles.tagPill}>
                <Text style={styles.tagText}>
                    {product.status === 'ACTIVE' ? '판매중' : product.status === 'SOLD_OUT' ? '품절' : '숨김'}
                </Text>
            </View>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 판매자(상점) 정보 */}
        <TouchableOpacity style={styles.sellerSection} onPress={() => router.push(`/shop/${seller?.id}`)}>
          <View style={styles.sellerInfo}>
            <View style={styles.avatarPlaceholder}>
                <Text style={{color:'#fff', fontWeight: 'bold'}}>{seller?.name?.[0] || 'S'}</Text>
            </View>
            <View>
              <Text fontWeight="bold" style={{fontSize: 16}}>{seller?.name}</Text>
              <Text style={styles.sellerLocation}>{seller?.address}</Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#999" />
        </TouchableOpacity>

        <View style={styles.divider} />

        {/* 상품 설명 섹션 */}
        <View style={styles.descSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>상품 설명</Text>
          <Text style={styles.descriptionText}>{product.description}</Text>
        </View>

        {/* 리뷰 섹션 (생략) */}
        <View style={{height: 100}} /> 
      </ScrollView>

      {/* 💡 [핵심 UI 분기 처리] 하단 버튼 바: 식당 vs 상점 */}
      <View style={styles.bottomBar}>
        {isRestaurant ? (
          // 🍽️ 식당(음식점/카페)일 경우 (Figma 7 컨셉 반영)
          <>
            <TouchableOpacity 
              style={[styles.cartBtn, styles.callBtn]} 
              onPress={() => Linking.openURL(`tel:${seller?.phone || '02-1234-5678'}`)}
            >
              <Ionicons name="call" size={18} color="#00A859" style={{marginRight: 6}} />
              <Text fontWeight="bold" style={[styles.cartBtnText, { color: '#00A859' }]}>전화하기</Text>
            </TouchableOpacity>
            
            <TouchableOpacity style={styles.buyBtn} onPress={() => Alert.alert('방문 예약', '방문 예약 페이지로 이동합니다.')}>
              <Text fontWeight="bold" style={styles.buyBtnText}>방문 예약하기</Text>
            </TouchableOpacity>
          </>
        ) : (
          // 🛍️ 상점(마트/반찬 등)일 경우 (Figma 8 완벽 반영)
          <>
            <TouchableOpacity 
              style={[styles.cartBtn, styles.shopCartBtn]} 
              onPress={handleAddToCart}
              disabled={product.status === 'SOLD_OUT'}
            >
              <Text fontWeight="bold" style={[styles.cartBtnText, styles.shopCartBtnText]}>장바구니</Text>
            </TouchableOpacity>
            
            <TouchableOpacity 
              style={[styles.buyBtn, styles.shopBuyBtn]} 
              onPress={handleBuyNow}
              disabled={product.status === 'SOLD_OUT'}
            >
              <Text fontWeight="bold" style={styles.buyBtnText}>구매하기</Text>
            </TouchableOpacity>
          </>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  imageContainer: { width: width, height: width, position: 'relative' },
  productImage: { width: '100%', height: '100%' },
  backButton: { position: 'absolute', top: 20, left: 20, backgroundColor: 'rgba(0,0,0,0.3)', padding: 8, borderRadius: 20 },
  infoSection: { padding: 20 },
  titleRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10, alignItems: 'center' },
  categoryText: { color: '#888', fontSize: 13 },
  productTitle: { fontSize: 22, color: '#333', marginBottom: 10, fontWeight: 'bold' },
  priceRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  originalPrice: { textDecorationLine: 'line-through', color: '#bbb', marginRight: 10, fontSize: 15 },
  currentPrice: { fontSize: 24, color: '#00A859', fontWeight: 'bold' },
  tagRow: { flexDirection: 'row' },
  tagPill: { backgroundColor: '#F5FDF8', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 4, marginRight: 8 },
  tagText: { color: '#00A859', fontSize: 12, fontWeight: 'bold' },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  sellerSection: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 20 },
  sellerInfo: { flexDirection: 'row', alignItems: 'center' },
  avatarPlaceholder: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#00A859', justifyContent: 'center', alignItems: 'center', marginRight: 12 },
  sellerLocation: { fontSize: 13, color: '#888', marginTop: 4 },
  descSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 15, fontWeight: 'bold' },
  descriptionText: { fontSize: 15, color: '#444', lineHeight: 24 },
  bottomBar: { flexDirection: 'row', padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  
  // 공통 버튼 스타일
  cartBtn: { flex: 1, paddingVertical: 16, borderRadius: 8, alignItems: 'center', justifyContent: 'center', marginRight: 10 },
  buyBtn: { flex: 2, paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  cartBtnText: { color: '#00A859', fontSize: 16, fontWeight: 'bold' },
  buyBtnText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },

  // 🍽️ 식당용 스타일 (Figma 7)
  callBtn: { backgroundColor: '#E8F5E9', borderWidth: 1, borderColor: '#00A859' },
  
  // 🛍️ 상점용 스타일 (Figma 8)
  shopCartBtn: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#00A859' },
  shopCartBtnText: { color: '#00A859' },
  shopBuyBtn: { backgroundColor: '#00A859' }
});