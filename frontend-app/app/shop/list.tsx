import React, { useState, useRef, useCallback } from 'react';
import { View, StyleSheet, FlatList, Image, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect, useLocalSearchParams } from 'expo-router'; 
import { Text } from '../../components/CustomText';

import { regionApi } from '../../api/region';
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';
import { favoriteApi } from '../../api/favorite';
import { reviewApi } from '../../api/review';

export default function ShopListScreen() {
  const router = useRouter();
  
  const { regionId } = useLocalSearchParams();

  const [selectedCategoryId, setSelectedCategoryId] = useState<number>(0);
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [hasRegion, setHasRegion] = useState(false);

  const categoryListRef = useRef<FlatList<any>>(null);

  const handleCategoryPress = (id: number, index: number) => {
    setSelectedCategoryId(id);
    
    setTimeout(() => {
      try {
        categoryListRef.current?.scrollToIndex({
          index,
          animated: true,
          viewPosition: 0.5,
        });
      } catch (e) {
        console.log("스크롤 이동 실패 (안전망):", e);
      }
    }, 50); 
  };

  useFocusEffect(
    useCallback(() => {
      let isActive = true;

      const fetchShops = async () => {
        if (shopList.length === 0) setIsLoading(true);
        try {
          let targetRegionId = regionId ? Number(regionId) : null;

          if (!targetRegionId) {
            const res = await regionApi.getMyRegions();
            const primary = (res.data || []).find((r: any) => r.isPrimary === true);
            if (primary) targetRegionId = primary.regionId;
          }

          if (targetRegionId) {
            if (isActive) setHasRegion(true);

            const categoryParam = selectedCategoryId === 0 ? undefined : selectedCategoryId;
            const shopRes = await shopApi.getShops({ 
              regionId: targetRegionId, 
              categoryId: categoryParam,
              size: 20 
            });
            
            // 데이터 방어 코드 적용
            let shops = shopRes.data?.content || shopRes.data || [];

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
                    favoriteCount: favCountRes?.data?.data ?? shop.favoriteCount ?? 0,
                    isFavorited: checkRes?.data?.data?.favorited ?? false,
                    reviewCount: reviewRes?.content?.length ?? shop.reviewCount ?? 0,
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
    }, [selectedCategoryId, regionId]) // ✨ regionId 의존성 추가
  );

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  const handleListToggleFavorite = async (storeId: number, currentStatus: boolean) => {
    try {
      const res = await favoriteApi.toggleFavorite('STORE', storeId);
      const { favorited, favoriteCount: newCount } = res.data.data;

      setShopList(prevList => 
        prevList.map(shop => 
          shop.storeId === storeId 
            ? { ...shop, isFavorited: favorited, favoriteCount: newCount } 
            : shop
        )
      );
    } catch (error) {
      Alert.alert("알림", "찜 상태 변경에 실패했습니다.");
    }
  };

  const renderShopCard = ({ item }: any) => {
    const thumbnailUrl = item.thumbnailUrl || 'https://via.placeholder.com/300/E8F5E9/00A859?text=Store';
    
    const rating = item.rating || 0;
    const reviewCount = item.reviewCount || 0;
    const favoriteCount = item.favoriteCount || 0;
    const isFavorited = item.isFavorited || false;

    return (
      <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.storeId}`)}>
        <Image source={{ uri: thumbnailUrl }} style={styles.cardImage} />
        
        <View style={styles.cardTitleRow}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
          
          <TouchableOpacity onPress={() => handleListToggleFavorite(item.storeId, isFavorited)}>
            <Ionicons 
              name={isFavorited ? "heart" : "heart-outline"} 
              size={20} 
              color={isFavorited ? "#FF5252" : "#999"} 
            />
          </TouchableOpacity>
        </View>

        <View style={styles.ratingRow}>
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

      <View style={styles.categoryWrapper}>
        <FlatList
          ref={categoryListRef}
          data={SHOP_CATEGORIES || []}
          horizontal
          showsHorizontalScrollIndicator={false}
          ListHeaderComponent={<View style={{ width: 20 }} />}
          ListFooterComponent={<View style={{ width: 20 }} />}
          keyExtractor={(item) => item.id.toString()}
          renderItem={({ item, index }) => (
            <TouchableOpacity 
              style={[styles.categoryPill, selectedCategoryId === item.id && styles.categoryPillActive]}
              onPress={() => handleCategoryPress(item.id, index)} 
            >
              <Text style={[styles.categoryText, selectedCategoryId === item.id && styles.categoryTextActive]}>
                {item.name}
              </Text>
            </TouchableOpacity>
          )}
          getItemLayout={(data, index) => ({ length: 80, offset: 80 * index, index })}
          onScrollToIndexFailed={(info) => {
            const wait = new Promise(resolve => setTimeout(resolve, 300));
            wait.then(() => {
              categoryListRef.current?.scrollToIndex({ index: info.index, animated: true, viewPosition: 0.5 });
            });
          }}
        />
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
  categoryWrapper: { marginBottom: 15 },
  categoryPill: { backgroundColor: '#F5F5F5', paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, marginRight: 8 },
  categoryPillActive: { backgroundColor: '#00A859' },
  categoryText: { color: '#666', fontSize: 14 },
  categoryTextActive: { color: '#fff', fontWeight: 'bold' },
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
  footerRow: { flexDirection: 'row', alignItems: 'center', marginTop: 4 },
  footerItem: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#F5F5F5', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  footerText: { fontSize: 11, color: '#666', marginLeft: 4 }
});