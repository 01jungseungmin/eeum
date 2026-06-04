// 📄 product/[id].tsx 

import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, Alert, ActivityIndicator, Linking } from 'react-native';
import { Text } from '../../components/CustomText'; 
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { shopApi } from '../../api/shop';

const { width } = Dimensions.get('window');

export default function ProductDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  
  const [isLiked, setIsLiked] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [product, setProduct] = useState<any>(null);

  const productIdNum = typeof id === 'string' ? Number(id) : 1;

  useEffect(() => {
    const fetchProductDetail = async () => {
      try {
        setIsLoading(true);
        const data = await shopApi.getProductDetail(productIdNum);
        setProduct(data);
      } catch (e) {
        Alert.alert("오류", "상품 정보를 불러오지 못했습니다.");
        router.back();
      } finally {
        setIsLoading(false);
      }
    };

    if (productIdNum) fetchProductDetail();
  }, [productIdNum]);

  const handleAddToCart = async () => {
    Alert.alert("장바구니", `[${product.name}] 상품을 담았습니다!`, [
      { text: "계속 쇼핑", style: "cancel" },
      { text: "장바구니 가기", onPress: () => router.push('/cart') }
    ]);
  };

  if (isLoading || !product) {
    return <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>;
  }

  const hasEvent = product.hasEvent;
  const currentPrice = hasEvent ? product.eventPrice : product.price;
  const productImageUrl = product.images?.[0]?.imageUrl || 'https://via.placeholder.com/600x600/E8F5E9/00A859?text=Product';

  // 💡 백엔드에서 내려주는 데이터에 따라 식당/상점 구분 (임시로 상점 처리)
  const isRestaurant = product.categoryId === 1 || product.categoryId === 2; 

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.imageContainer}>
          <Image source={{ uri: productImageUrl }} style={styles.productImage} />
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Ionicons name="chevron-back" size={28} color="#fff" />
          </TouchableOpacity>
        </View>

        <View style={styles.infoSection}>
          <View style={styles.titleRow}>
            <Text style={styles.categoryText}>{product.storeName}</Text> 
            <TouchableOpacity onPress={() => setIsLiked(!isLiked)}>
              <Ionicons name={isLiked ? "heart" : "heart-outline"} size={24} color={isLiked ? "#FF5252" : "#999"} />
            </TouchableOpacity>
          </View>
          <Text fontWeight="bold" style={styles.productTitle}>{product.name}</Text>
          <View style={styles.priceRow}>
            {hasEvent && <Text style={styles.originalPrice}>{product.price?.toLocaleString()}원</Text>}
            <Text fontWeight="bold" style={styles.currentPrice}>{currentPrice?.toLocaleString()}원</Text>
          </View>
        </View>
        <View style={styles.divider} />
        
        <View style={styles.descSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>상품 설명</Text>
          <Text style={styles.descriptionText}>{product.description}</Text>
        </View>

        <View style={{height: 100}} /> 
      </ScrollView>

      <View style={styles.bottomBar}>
        {isRestaurant ? (
          <TouchableOpacity 
            style={[styles.cartBtn, { backgroundColor: '#00A859' }]} 
            onPress={handleAddToCart}
          >
            <Text fontWeight="bold" style={{ color: '#fff', fontSize: 16 }}>픽업 장바구니 담기</Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity 
            style={[styles.cartBtn, { backgroundColor: '#00A859' }]} 
            onPress={handleAddToCart}
            disabled={product.status === 'SOLD_OUT'}
          >
            <Text fontWeight="bold" style={{ color: '#fff', fontSize: 16 }}>
              {product.status === 'SOLD_OUT' ? '품절된 상품입니다' : '장바구니 담기'}
            </Text>
          </TouchableOpacity>
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
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  descSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 15, fontWeight: 'bold' },
  descriptionText: { fontSize: 15, color: '#444', lineHeight: 24 },
  bottomBar: { flexDirection: 'row', padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  cartBtn: { flex: 1, paddingVertical: 16, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
});