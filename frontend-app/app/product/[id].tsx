import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, Alert, ActivityIndicator } from 'react-native';
import { Text } from '../../components/CustomText'; 
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

// 🚧 [백엔드 연동]
// import { productApi } from '../../api/product';

const { width } = Dimensions.get('window');

const MOCK_PRODUCT = {
  id: '1',
  title: '[콜리브리] 유기농 엑스트라버진 올리브 오일',
  category: '식품 · 2시간 전',
  price: 13000,
  originalPrice: 18000,
  tags: ['#프리미엄', '#이탈리아산', '#건강식품'],
  description: '이탈리아 토스카나 지역에서 생산된 프리미엄 엑스트라버진 올리브유입니다. 신선한 풍미가 일품이며 샐러드나 파스타에 곁들이기 좋습니다.',
  rating: 4.8,
  reviewCount: 89,
  seller: { name: '현대식품관', location: '송파동', rating: 4.9 },
  img: 'https://via.placeholder.com/500/E8F5E9/00A859?text=Olive+Oil'
};

const MOCK_REVIEWS = [
  { id: 'r1', user: '김*', rating: 5, date: '2026.04.15', content: '맛이 정말 진하고 좋아요! 샐러드 드레싱으로 최고입니다.' },
  { id: 'r2', user: '이**', rating: 4, date: '2026.04.12', content: '배송도 빠르고 포장도 꼼꼼해서 만족합니다.' }
];

export default function ProductDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  const [isLiked, setIsLiked] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [product, setProduct] = useState<any>(MOCK_PRODUCT);

  /* 🚧 [백엔드 연동 켜기] 실제 백엔드 연동 시 아래 주석을 해제하세요!
  useEffect(() => {
    if (id) {
      const fetchProductDetail = async () => {
        setIsLoading(true);
        try {
          const res = await productApi.getProductDetail(id as string);
          setProduct(res.data); // 서버 데이터로 연동 완료
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

  if (isLoading) {
    return <View style={{flex:1, justifyContent:'center', alignItems:'center'}}><ActivityIndicator size="large" color="#00A859" /></View>;
  }

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.imageContainer}>
          <Image source={{ uri: product.img }} style={styles.productImage} />
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}><Ionicons name="chevron-back" size={28} color="#333" /></TouchableOpacity>
        </View>

        <View style={styles.infoSection}>
          <View style={styles.titleRow}>
            <Text style={styles.categoryText}>{product.category}</Text>
            <TouchableOpacity onPress={() => setIsLiked(!isLiked)}><Ionicons name={isLiked ? "heart" : "heart-outline"} size={24} color={isLiked ? "#FF5252" : "#999"} /></TouchableOpacity>
          </View>
          <Text fontWeight="bold" style={styles.productTitle}>{product.title}</Text>
          <View style={styles.priceRow}>
            <Text style={styles.originalPrice}>{product.originalPrice.toLocaleString()}원</Text>
            <Text fontWeight="bold" style={styles.currentPrice}>{product.price.toLocaleString()}원</Text>
          </View>
          <View style={styles.tagRow}>
            {product.tags.map((tag: string) => (<View key={tag} style={styles.tagPill}><Text style={styles.tagText}>{tag}</Text></View>))}
          </View>
        </View>

        <View style={styles.divider} />

        <TouchableOpacity style={styles.sellerSection}>
          <View style={styles.sellerInfo}>
            <View style={styles.avatarPlaceholder}><Text style={{color:'#fff'}}>M</Text></View>
            <View>
              <Text fontWeight="bold">{product.seller.name}</Text>
              <Text style={styles.sellerLocation}>{product.seller.location}</Text>
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
            <Text fontWeight="bold" style={styles.sectionTitle}>상품 리뷰 {product.reviewCount}</Text>
            <View style={styles.ratingRow}><Ionicons name="star" size={16} color="#FFD700" /><Text fontWeight="bold">{product.rating}</Text></View>
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
        <TouchableOpacity style={styles.cartBtn} onPress={handleAddToCart}><Text fontWeight="bold" style={styles.cartBtnText}>장바구니</Text></TouchableOpacity>
        <TouchableOpacity style={styles.buyBtn} onPress={handleBuyNow}><Text fontWeight="bold" style={styles.buyBtnText}>구매하기</Text></TouchableOpacity>
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
  sellerLocation: { fontSize: 12, color: '#888' },
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