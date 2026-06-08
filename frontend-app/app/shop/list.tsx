import React, { useState, useCallback } from 'react';
import { View, StyleSheet, FlatList, Image, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router'; 
import { Text } from '../../components/CustomText';

import { regionApi } from '../../api/region';
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';
import { favoriteApi } from '../../api/favorite';
import { reviewApi } from '../../api/review';

export default function ShopListScreen() {
  const router = useRouter();

  const [selectedCategoryId, setSelectedCategoryId] = useState<number>(0);
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [hasRegion, setHasRegion] = useState(false);

  // --- [1. 화면에 들어올 때마다 최신 데이터 받아오기] ---
  useFocusEffect(
    useCallback(() => {
      let isActive = true;

      const fetchShops = async () => {
        setIsLoading(true);
        try {
          // 1. 내 동네 찾기
          const res = await regionApi.getMyRegions();
          const primary = (res.data || []).find((r: any) => r.isPrimary === true);

          if (primary) {
            setHasRegion(true);
            // 2. 상점 목록 불러오기
            const shopRes = await shopApi.getShops({ regionId: primary.regionId, size: 20 });
            let shops = shopRes.data?.content || shopRes.data || [];

            // ✨ [중요] 백엔드 목록 API가 찜/리뷰를 제대로 안 준다면, 프론트에서 강제로 덧씌웁니다.
            const updatedShops = await Promise.all(
              shops.map(async (shop: any) => {
                try {
                  const [favCountRes, checkRes, reviewRes] = await Promise.all([
                    favoriteApi.getFavoriteCount('STORE', shop.storeId).catch(() => null),
                    favoriteApi.checkFavorite('STORE', shop.storeId).catch(() => null),
                    reviewApi.getReviews(shop.storeId).catch(() => null)
                  ]);

                  return {
                    ...shop,
                    // 실제 API에서 받아온 값으로 덮어쓰기 (실패 시 기존 값 유지)
                    favoriteCount: favCountRes?.data?.data ?? shop.favoriteCount ?? 0,
                    isFavorited: checkRes?.data?.data?.favorited ?? false,
                    reviewCount: reviewRes?.content?.length ?? shop.reviewCount ?? 0,
                    // 평점 계산 (임시: 리뷰가 있으면 평균, 없으면 0)
                    rating: reviewRes?.content?.length 
                      ? reviewRes.content.reduce((acc: number, cur: any) => acc + cur.rating, 0) / reviewRes.content.length 
                      : shop.rating ?? 0,
                  };
                } catch (e) {
                  return shop;
                }
              })
            );

            if (isActive) setShopList(updatedShops);
          } else {
            if (isActive) setHasRegion(false);
          }
        } catch (e) {
          console.error(e);
        } finally {
          if (isActive) setIsLoading(false);
        }
      };

      fetchShops();

      return () => { isActive = false; };
    }, [selectedCategoryId])
  );

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  // --- [2. 상점 카드 렌더링 (0개일 때 회색 처리 포함)] ---
  const renderShopCard = ({ item }: any) => {
    const thumbnailUrl = item.thumbnailUrl || 'https://via.placeholder.com/300/E8F5E9/00A859?text=Store';
    
    // 데이터 방어 코드 (null, undefined 처리)
    const rating = item.rating || 0;
    const reviewCount = item.reviewCount || 0;
    const favoriteCount = item.favoriteCount || 0;
    const isFavorited = item.isFavorited || false;

    return (
      <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.storeId}`)}>
        <Image source={{ uri: thumbnailUrl }} style={styles.cardImage} />
        
        <View style={styles.cardTitleRow}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
          {/* ✨ 찜 여부에 따라 하트 색상 변경 */}
          <Ionicons name={isFavorited ? "heart" : "heart-outline"} size={20} color={isFavorited ? "#FF5252" : "#999"} />
        </View>

        <View style={styles.ratingRow}>
          {/* ✨ 리뷰가 0개면 회색 별, 1개 이상이면 노란 별 */}
          <Ionicons name="star" size={14} color={reviewCount > 0 ? "#FFD700" : "#E0E0E0"} />
          
          {reviewCount > 0 ? (
            <>
              <Text fontWeight="bold" style={styles.ratingText}>{rating.toFixed(1)}</Text>
              <Text style={styles.reviewText}>({reviewCount})</Text>
            </>
          ) : (
            <Text style={[styles.ratingText, { color: '#999', fontSize: 12, fontWeight: 'normal' }]}>평가 없음</Text>
          )}
        </View>

        <Text style={styles.locationText}>{item.categoryName || getCategoryName(item.categoryId)}</Text>

        <View style={styles.footerRow}>
          <View style={styles.footerItem}>
            {/* ✨ 찜이 0개면 빈 하트, 1개 이상이면 꽉 찬 하트 */}
            <Ionicons name={favoriteCount > 0 ? "heart" : "heart-outline"} size={12} color={favoriteCount > 0 ? "#FF5252" : "#999"} />
            <Text style={styles.footerText}>{favoriteCount}</Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 10 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>우리 동네 상점</Text>
      </View>

      <View style={styles.listHeader}>
        <Text style={styles.totalText}>총 {(shopList || []).length}개</Text>
      </View>

      {isLoading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : !hasRegion ? ( 
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Ionicons name="location-outline" size={48} color="#CCC" style={{ marginBottom: 10 }} />
          <Text style={{ color: '#888', fontSize: 16 }}>동네를 먼저 설정해 주세요!</Text>
        </View>
      ) : shopList.length === 0 ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ color: '#888' }}>해당 조건에 맞는 상점이 없습니다.</Text>
        </View>
      ) : (
        <FlatList
          data={shopList}
          keyExtractor={(item) => item.storeId.toString()}
          renderItem={renderShopCard}
          numColumns={2}
          columnWrapperStyle={styles.rowWrapper}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ paddingBottom: 30 }}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15 },
  headerTitle: { fontSize: 18, color: '#333' },
  listHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, marginBottom: 15 },
  totalText: { fontSize: 14, color: '#333', fontWeight: 'bold' },
  rowWrapper: { justifyContent: 'space-between', paddingHorizontal: 20 },
  cardContainer: { width: '48%', marginBottom: 25 },
  cardImage: { width: '100%', aspectRatio: 1, borderRadius: 12, marginBottom: 10 },
  cardTitleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  shopName: { fontSize: 16, color: '#333', flex: 1, marginRight: 5 },
  ratingRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  ratingText: { fontSize: 13, color: '#333', marginLeft: 4, marginRight: 4 },
  reviewText: { fontSize: 12, color: '#888' },
  locationText: { fontSize: 12, color: '#888', marginBottom: 6 },
  footerRow: { flexDirection: 'row', alignItems: 'center' },
  footerItem: { flexDirection: 'row', alignItems: 'center', marginRight: 10 },
  footerText: { fontSize: 11, color: '#999', marginLeft: 4 },
});