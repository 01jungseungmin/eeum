import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams, useFocusEffect } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { shopApi } from '../../api/shop';
import { favoriteApi } from '../../api/favorite';
import { reviewApi } from '../../api/review';

const { width } = Dimensions.get('window');

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  
  const [isLoading, setIsLoading] = useState(true);
  const [shopDetail, setShopDetail] = useState<any>(null);
  const [shopProducts, setShopProducts] = useState<any[]>([]);
  const [isFavorited, setIsFavorited] = useState<boolean>(false);
  const [favoriteCount, setFavoriteCount] = useState<number>(0);
  const [shopReviews, setShopReviews] = useState<any[]>([]);

  const shopIdNum = typeof id === 'string' ? Number(id) : 1;

  // 1. 데이터 로딩
  useEffect(() => {
    const fetchShopData = async () => {
      try {
        setIsLoading(true);
        const [detailData, productsData, checkRes, countRes, reviewsRes] = await Promise.all([
          shopApi.getShopDetail(shopIdNum),
          shopApi.getShopProducts(shopIdNum),
          favoriteApi.checkFavorite('STORE', shopIdNum).catch(() => null),
          favoriteApi.getFavoriteCount('STORE', shopIdNum).catch(() => null),
          reviewApi.getReviews(shopIdNum).catch(() => null)
        ]);
        
        setShopReviews(reviewsRes?.content || reviewsRes?.data || []);
        setShopDetail(detailData);
        setShopProducts(productsData || []);
        if (checkRes?.data?.data) setIsFavorited(checkRes.data.data.favorited);
        if (countRes?.data) setFavoriteCount(countRes.data.data);
      } catch (e) {
        console.log("❌ 에러:", e);
        Alert.alert("오류", "정보를 불러오지 못했습니다.");
        router.back();
      } finally {
        setIsLoading(false);
      }
    };
    if (shopIdNum) fetchShopData();
  }, [shopIdNum]);

  // 2. 찜 토글 함수
  const handleToggleFavorite = async () => {
    try {
      const res = await favoriteApi.toggleFavorite('STORE', shopIdNum);
      const { favorited, favoriteCount: newCount } = res.data.data;
      setIsFavorited(favorited);
      setFavoriteCount(newCount);
    } catch (error) {
      Alert.alert("알림", "찜 상태를 변경할 수 없습니다.");
    }
  };

  if (isLoading) {
    return (
      <View style={{flex:1, justifyContent:'center', alignItems:'center', backgroundColor: '#fff'}}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  if (!shopDetail) return null;

  const categoryName = shopDetail.categoryName || '기타';
  const coverImageUrl = shopDetail.images?.[0]?.imageUrl || 'https://via.placeholder.com/600x400/E8F5E9/00A859?text=Store';
  const isRestaurant = shopDetail.categoryId === 1 || shopDetail.categoryId === 2;

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 150 }}>
        <View style={styles.coverContainer}>
          <Image source={{ uri: coverImageUrl }} style={styles.coverImg} />
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}>
            <Ionicons name="chevron-back" size={28} color="#fff" />
          </TouchableOpacity>
        </View>

        <View style={styles.mainInfo}>
          <View style={styles.categoryBadge}><Text style={styles.categoryText}>{categoryName}</Text></View>
          <View style={styles.nameRow}>
            <Text fontWeight="bold" style={styles.shopName}>{shopDetail.name}</Text>
            <View style={styles.ratingRow}>
              <TouchableOpacity onPress={handleToggleFavorite} style={{ flexDirection: 'row', alignItems: 'center', marginRight: 12 }}>
                <Ionicons name={isFavorited ? "heart" : "heart-outline"} size={22} color={isFavorited ? "#FF5252" : "#999"} />
                <Text style={{ marginLeft: 4, fontSize: 16, color: '#333' }}>{favoriteCount}</Text>
              </TouchableOpacity>
              <Ionicons name="star" size={18} color="#FFD700" />
              <Text fontWeight="bold" style={styles.ratingText}>{shopDetail.rating?.toFixed(1) || '0.0'}</Text>
            </View>
          </View>
          <View style={styles.contactRow}><Ionicons name="location-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.address}</Text></View>
          <View style={styles.contactRow}><Ionicons name="call-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.phone}</Text></View>
        </View>

        <View style={styles.divider} />

        <View style={styles.menuSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>메뉴</Text>
          {shopProducts.map((menu: any) => (
            <TouchableOpacity key={menu.productId} style={styles.menuCard} onPress={() => router.push(`/product/${menu.productId}`)}>
              <View style={styles.menuTextContainer}>
                <Text fontWeight="bold" style={styles.menuName}>{menu.name}</Text>
                <Text style={styles.menuDesc} numberOfLines={2}>{menu.description}</Text>
                <Text fontWeight="bold" style={styles.menuPrice}>{menu.price?.toLocaleString()}원</Text>
              </View>
              {menu.thumbnailUrl && <Image source={{ uri: menu.thumbnailUrl }} style={styles.menuImg} />}
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.divider} />

        <View style={styles.reviewSection}>
          <View style={styles.reviewHeader}>
            <Text fontWeight="bold" style={styles.sectionTitle}>상점 리뷰</Text>
            <TouchableOpacity 
              style={styles.reviewWriteBtn}
              onPress={() => router.push({
                pathname: '/review/write' as any,
                params: { storeId: shopDetail.storeId }
              })}
            >
              <Text style={styles.reviewWriteBtnText}>리뷰 쓰기</Text>
            </TouchableOpacity>
          </View>

          {shopReviews.length === 0 ? (
            <Text style={styles.emptyReviewText}>아직 등록된 리뷰가 없습니다.</Text>
          ) : (
            shopReviews.slice(0, 3).map((review: any) => (
              <View key={review.storereviewId} style={styles.reviewCard}>
                <View style={styles.reviewUserRow}>
                  <Ionicons name="star" size={14} color="#FFD700" />
                  <Text style={styles.reviewRatingText}>{review.rating}</Text>
                  <Text style={styles.reviewWriterText}>{review.nickname || '익명'}</Text>
                </View>
                <Text style={styles.reviewContentText}>{review.content}</Text>
                {review.images && review.images.length > 0 && (
                  <Image source={{ uri: review.images[0].imageUrl }} style={styles.reviewImage} />
                )}
              </View>
            ))
          )}
          
          {/* 리뷰가 3개 이상일 때만 더보기 버튼 노출 */}
          {shopReviews.length > 0 && (
            <TouchableOpacity 
              style={styles.moreReviewBtn}
              onPress={() => router.push({
                pathname: '/review/list' as any,
                params: { storeId: shopDetail.storeId }
              })}
            >
              <Text style={styles.moreReviewBtnText}>리뷰 더 보기</Text>
              <Ionicons name="chevron-forward" size={16} color="#666" />
            </TouchableOpacity>
          )}
        </View>

      </ScrollView>

      {/* 하단 버튼 영역 */}
      <View style={styles.bottomBar}>
        {isRestaurant ? (
          <View style={{ width: '100%', gap: 10 }}>
            <TouchableOpacity style={styles.primaryBtn} onPress={() => router.push({ pathname: '/restaurant/reservation' as any, params: { storeId: shopDetail.storeId }})}>
              <Text fontWeight="bold" style={styles.primaryBtnText}>방문 예약하기</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <TouchableOpacity style={styles.primaryBtn} onPress={() => router.push('/cart')}>
            <Text fontWeight="bold" style={styles.primaryBtnText}>장바구니 보기</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  coverContainer: { width: '100%', height: 250, position: 'relative' },
  coverImg: { width: '100%', height: '100%' },
  backBtn: { position: 'absolute', top: 20, left: 20, backgroundColor: 'rgba(0,0,0,0.3)', padding: 8, borderRadius: 20 },
  mainInfo: { padding: 20 },
  categoryBadge: { backgroundColor: '#E8F5E9', alignSelf: 'flex-start', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 4, marginBottom: 10 },
  categoryText: { color: '#00A859', fontSize: 12 },
  nameRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 15 },
  shopName: { fontSize: 24, color: '#333' },
  ratingRow: { flexDirection: 'row', alignItems: 'center' },
  ratingText: { fontSize: 18, color: '#333', marginLeft: 5 },
  contactRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  contactText: { fontSize: 14, color: '#666', marginLeft: 10 },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  menuSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 20 },
  menuCard: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  menuTextContainer: { flex: 1, paddingRight: 15 },
  menuName: { fontSize: 16, color: '#333', marginBottom: 5 },
  menuDesc: { fontSize: 13, color: '#888', marginBottom: 10 },
  menuPrice: { fontSize: 16, color: '#333' },
  menuImg: { width: 100, height: 100, borderRadius: 8 },
  
  reviewSection: { padding: 20 },
  reviewHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  reviewWriteBtn: { paddingHorizontal: 12, paddingVertical: 6, backgroundColor: '#E8F5E9', borderRadius: 4 },
  reviewWriteBtnText: { color: '#00A859', fontSize: 13, fontWeight: 'bold' },
  emptyReviewText: { color: '#888', textAlign: 'center', paddingVertical: 20 },
  reviewCard: { marginBottom: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0', paddingBottom: 15 },
  reviewUserRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 5 },
  reviewRatingText: { fontWeight: 'bold', marginLeft: 4, fontSize: 14, color: '#333' },
  reviewWriterText: { color: '#888', marginLeft: 10, fontSize: 12 },
  reviewContentText: { fontSize: 14, color: '#333', lineHeight: 20 },
  reviewImage: { width: 80, height: 80, borderRadius: 8, marginTop: 10 },
  moreReviewBtn: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 15 },
  moreReviewBtnText: { color: '#666', fontSize: 14, marginRight: 4, fontWeight: '500' },

  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  primaryBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  primaryBtnText: { color: '#fff', fontSize: 16 },
  secondaryBtn: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  secondaryBtnText: { color: '#00A859', fontSize: 16 }
});