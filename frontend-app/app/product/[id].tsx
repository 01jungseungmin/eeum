import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, Alert, ActivityIndicator } from 'react-native';
import { Text } from '../../components/CustomText'; 
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { DUMMY_SHOPS } from '../../constants/shopDummyData';
// 🚧 [백엔드 연동]
// import { productApi } from '../../api/product';

const { width } = Dimensions.get('window');

// 더미 리뷰
const MOCK_REVIEWS = [
  { id: 'r1', user: '김*', rating: 5, date: '2026.04.15', content: '정말 만족스럽습니다! 강력 추천해요.' },
  { id: 'r2', user: '이**', rating: 4, date: '2026.04.12', content: '포장도 깔끔하고 배송도 빨랐어요.' }
];

export default function ProductDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  const [isLiked, setIsLiked] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  
  // 더미 데이터에서 상품 추출
  const [product, setProduct] = useState<any>(null);
  const [seller, setSeller] = useState<any>(null);

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

  /* 🚧 [백엔드 연동 켜기] 실제 백엔드 연동 시 아래 주석 해제!
  useEffect(() => {
    if (id) {
      const fetchProductDetail = async () => {
        setIsLoading(true);
        try {
          const res = await productApi.getProductDetail(id as string);
          setProduct(res.data.product); 
          setSeller(res.data.seller);
        } catch (e) {
          console.error('상품 상세 API 로딩 실패:', e);
        } finally {
          setIsLoading(false);
        }
      };
      fetchProductDetail();
    }
  }, [id]);
  */

  const handleAddToCart = async () => {
    try {
      // 🚧 [장바구니 API 연동 주석]
      // await cartApi.addToCart({ productId: id, quantity: 1 });
      Alert.alert("장바구니", "장바구니에 상품을 담았습니다!");
    } catch (error) {
      Alert.alert("오류", "장바구니 담기에 실패했습니다.");
    }
  };

  const handleBuyNow = () => {
    // 🚧 [결제화면 라우팅 주석]
    // router.push({ pathname: '/order/checkout', params: { productId: id } });
    Alert.alert("구매하기", "주문/결제 페이지로 이동합니다 (준비 중)");
  };

  if (isLoading || !product) {
    return <View style={{flex:1, justifyContent:'center', alignItems:'center'}}><ActivityIndicator size="large" color="#00A859" /></View>;
  }

  // 할인 여부 계산
  const hasEvent = !!product.eventPrice;
  const currentPrice = hasEvent ? product.eventPrice : product.price;

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.imageContainer}>
          <Image source={{ uri: product.imageUrl }} style={styles.productImage} />
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}><Ionicons name="chevron-back" size={28} color="#333" /></TouchableOpacity>
        </View>

        <View style={styles.infoSection}>
          <View style={styles.titleRow}>
            <Text style={styles.categoryText}>{seller?.name} · {product.type === 'RESERVATION' ? '예약' : '일반'}</Text>
            <TouchableOpacity onPress={() => setIsLiked(!isLiked)}><Ionicons name={isLiked ? "heart" : "heart-outline"} size={24} color={isLiked ? "#FF5252" : "#999"} /></TouchableOpacity>
          </View>
          <Text fontWeight="bold" style={styles.productTitle}>{product.name}</Text>
          <View style={styles.priceRow}>
            {hasEvent && <Text style={styles.originalPrice}>{product.price.toLocaleString()}원</Text>}
            <Text fontWeight="bold" style={styles.currentPrice}>{currentPrice.toLocaleString()}원</Text>
          </View>
          <View style={styles.tagRow}>
             <View style={styles.tagPill}><Text style={styles.tagText}>{product.status === 'ACTIVE' ? '판매중' : '품절'}</Text></View>
             {product.stock && <View style={styles.tagPill}><Text style={styles.tagText}>남은수량: {product.stock}개</Text></View>}
          </View>
        </View>

        <View style={styles.divider} />

        <TouchableOpacity style={styles.sellerSection} onPress={() => router.push(`/shop/${seller?.id}`)}>
          <View style={styles.sellerInfo}>
            <View style={styles.avatarPlaceholder}><Text style={{color:'#fff'}}>{seller?.name?.[0] || 'S'}</Text></View>
            <View>
              <Text fontWeight="bold">{seller?.name}</Text>
              <Text style={styles.sellerLocation}>{seller?.address}</Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#999" />
        </TouchableOpacity>

        <View style={styles.divider} />

        <View style={styles.descSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>상품 설명</Text>
          <Text style={styles.descriptionText}>{product.description}</Text>
        </View>

        <View style={styles.divider} />

        <View style={styles.reviewSection}>
          <View style={styles.sectionHeader}>
            <Text fontWeight="bold" style={styles.sectionTitle}>상품 리뷰 {seller?.reviewCount}</Text>
            <View style={styles.ratingRow}><Ionicons name="star" size={16} color="#FFD700" /><Text fontWeight="bold">{seller?.rating.toFixed(1)}</Text></View>
          </View>
          {MOCK_REVIEWS.map(review => (
            <View key={review.id} style={styles.reviewCard}>
              <View style={styles.reviewUserRow}>
                <Text fontWeight="bold" style={styles.reviewUser}>{review.user}</Text>
                <View style={styles.stars}>{[1,2,3,4,5].map(s => <Ionicons key={s} name="star" size={12} color={s <= review.rating ? "#FFD700" : "#E0E0E0"} />)}</View>
              </View>
              <Text style={styles.reviewContent}>{review.content}</Text>
              <Text style={styles.reviewDate}>{review.date}</Text>
            </View>
          ))}
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity 
          style={[styles.cartBtn, product.status === 'SOLD_OUT' && { backgroundColor: '#F5F5F5' }]} 
          onPress={handleAddToCart}
          disabled={product.status === 'SOLD_OUT'}
        >
          <Text fontWeight="bold" style={[styles.cartBtnText, product.status === 'SOLD_OUT' && { color: '#999' }]}>
            {product.status === 'SOLD_OUT' ? '품절' : '장바구니'}
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity 
          style={[styles.buyBtn, product.status === 'SOLD_OUT' && { backgroundColor: '#999' }]} 
          onPress={handleBuyNow}
          disabled={product.status === 'SOLD_OUT'}
        >
          <Text fontWeight="bold" style={styles.buyBtnText}>
             {product.type === 'RESERVATION' ? '예약하기' : '구매하기'}
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  imageContainer: { width: width, height: width, position: 'relative' },
  productImage: { width: '100%', height: '100%' },
  backButton: { position: 'absolute', top: 20, left: 20, backgroundColor: 'rgba(255,255,255,0.8)', padding: 8, borderRadius: 20 },
  infoSection: { padding: 20 },
  titleRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  categoryText: { color: '#888', fontSize: 13 },
  productTitle: { fontSize: 20, color: '#333', marginBottom: 10 },
  priceRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  originalPrice: { textDecorationLine: 'line-through', color: '#bbb', marginRight: 10 },
  currentPrice: { fontSize: 22, color: '#00A859' },
  tagRow: { flexDirection: 'row' },
  tagPill: { backgroundColor: '#F5FDF8', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 4, marginRight: 8 },
  tagText: { color: '#00A859', fontSize: 12 },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  sellerSection: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 20 },
  sellerInfo: { flexDirection: 'row', alignItems: 'center' },
  avatarPlaceholder: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#00A859', justifyContent: 'center', alignItems: 'center', marginRight: 12 },
  sellerLocation: { fontSize: 12, color: '#888', marginTop: 4 },
  descSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 15 },
  descriptionText: { fontSize: 15, color: '#444', lineHeight: 22 },
  reviewSection: { padding: 20, paddingBottom: 100 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  ratingRow: { flexDirection: 'row', alignItems: 'center' },
  reviewCard: { marginBottom: 20, borderBottomWidth: 1, borderBottomColor: '#F0F0F0', paddingBottom: 15 },
  reviewUserRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 5 },
  reviewUser: { fontSize: 14 },
  stars: { flexDirection: 'row' },
  reviewContent: { fontSize: 14, color: '#555', marginBottom: 5 },
  reviewDate: { fontSize: 12, color: '#999' },
  bottomBar: { flexDirection: 'row', padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  cartBtn: { flex: 1, backgroundColor: '#E8F5E9', paddingVertical: 15, borderRadius: 8, alignItems: 'center', marginRight: 10 },
  cartBtnText: { color: '#00A859' },
  buyBtn: { flex: 2, backgroundColor: '#00A859', paddingVertical: 15, borderRadius: 8, alignItems: 'center' },
  buyBtnText: { color: '#fff' }
});