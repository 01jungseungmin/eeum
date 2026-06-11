import React, { useState, useEffect, useRef, useCallback, } from 'react';
import { View, StyleSheet, FlatList, Image, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router'; 
import { Text } from '../../components/CustomText';

import { regionApi } from '../../api/region';
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';
import { favoriteApi } from '@/api/favorite';

export default function ShopListScreen() {
  const router = useRouter();

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

  // ✨ 카테고리 필터링(develop) + 지역 기반 조회(HEAD) 결합 로직
  useFocusEffect(
    useCallback(() => {
      const fetchShopsByPrimaryRegion = async () => {
        if (shopList.length === 0) {
          setIsLoading(true);
        }
        try {
          // 1. 내 동네 목록 전체 조회
          const res = await regionApi.getMyRegions();
          const regions = res.data || []; 
          
          // 2. 대표 동네(isPrimary) 찾기
          const primary = regions.find((r: any) => r.isPrimary === true);

          if (primary) {
            setHasRegion(true);
            
            // 카테고리 필터링 적용 (0이면 전체)
            const categoryParam = selectedCategoryId === 0 ? undefined : selectedCategoryId;
            
            const shopRes = await shopApi.getShops({ 
              regionId: primary.regionId, 
              categoryId: categoryParam, 
              size: 20 
            });
            
            const shops = shopRes.data?.content || [];

            // ✨ 핵심: 백엔드 목록 API가 주지 않는 찜(isFavorited) 상태를 개별적으로 조회해서 합쳐줍니다.
            const updatedShops = await Promise.all(
              shops.map(async (shop: any) => {
                try {
                  const [checkRes, countRes] = await Promise.all([
                    favoriteApi.checkFavorite('STORE', shop.storeId).catch(() => null),
                    favoriteApi.getFavoriteCount('STORE', shop.storeId).catch(() => null)
                  ]);

                  return {
                    ...shop,
                    // 서버 응답 구조에 맞게 안전하게 가공 (실패 시 기존값 유지)
                    isFavorited: checkRes?.data?.data?.favorited ?? false,
                    favoriteCount: countRes?.data?.data ?? countRes?.data ?? shop.favoriteCount ?? 0,
                  };
                } catch (e) {
                  return shop; // 에러 발생 시 원래 상점 데이터 유지
                }
              })
            );
            
            setShopList(updatedShops);
          } else {
            setHasRegion(false);
          }
        } catch (e) {
          console.error(e);
        } finally {
          setIsLoading(false);
        }
      };
      
      fetchShopsByPrimaryRegion();
    }, [selectedCategoryId])
  );

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  // ✨ 리스트 내 찜 토글 로직 (HEAD 유지)
  const handleListToggleFavorite = async (storeId: number, currentStatus: boolean) => {
    try {
      // 서버에 토글 요청
      const res = await favoriteApi.toggleFavorite('STORE', storeId);
      const { favorited, favoriteCount: newCount } = res.data.data;

      // 현재 목록(shopList)에서 해당 상점만 찾아서 상태 업데이트
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

  // ✨ 상점 카드 렌더링 (하트 UI 보존)
  const renderShopCard = ({ item }: any) => {
    const thumbnailUrl = item.thumbnailUrl || 'https://via.placeholder.com/300/E8F5E9/00A859?text=Store';

    return (
      <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.storeId}`)}>
        <Image source={{ uri: thumbnailUrl }} style={styles.cardImage} />
        <View style={styles.cardTitleRow}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
          
          {/* 하트 아이콘 영역 유지 */}
          <TouchableOpacity onPress={() => handleListToggleFavorite(item.storeId, item.isFavorited)}>
            <Ionicons 
              name={item.isFavorited ? "heart" : "heart-outline"} 
              size={20} 
              color={item.isFavorited ? "#FF5252" : "#999"} 
            />
          </TouchableOpacity>
        </View>
        <View style={styles.ratingRow}>
          <Ionicons name="star" size={14} color="#FFD700" />
          <Text fontWeight="bold" style={styles.ratingText}>{item.rating?.toFixed(1) || '0.0'}</Text>
          <Text style={styles.reviewText}>({item.reviewCount || 0})</Text>
        </View>
        <Text style={styles.locationText}>{item.categoryName || getCategoryName(item.categoryId)}</Text>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 1. 상단 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 10 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>우리 동네 상점</Text>
      </View>

      {/* 2. 카테고리 탭 */}
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

      {/* 3. 리스트 상단 (총 N개) */}
      <View style={styles.listHeader}>
        <Text style={styles.totalText}>총 {(shopList || []).length}개</Text>
        <TouchableOpacity style={styles.sortButton}>
          <Text style={styles.sortText}>최신순</Text>
          <Ionicons name="chevron-down" size={14} color="#666" />
        </TouchableOpacity>
      </View>

      {/* 4. 메인 상점 리스트 (조건부 렌더링) */}
      {isLoading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ): !hasRegion ? ( 
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
  sortButton: { flexDirection: 'row', alignItems: 'center' },
  sortText: { fontSize: 13, color: '#666', marginRight: 4 },
  rowWrapper: { justifyContent: 'space-between', paddingHorizontal: 20 },
  cardContainer: { width: '48%', marginBottom: 25 },
  cardImage: { width: '100%', aspectRatio: 1, borderRadius: 12, marginBottom: 10 },
  cardTitleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  shopName: { fontSize: 16, color: '#333', flex: 1, marginRight: 5 },
  ratingRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  ratingText: { fontSize: 13, color: '#333', marginLeft: 4, marginRight: 4 },
  reviewText: { fontSize: 12, color: '#888' },
  locationText: { fontSize: 12, color: '#888', marginBottom: 6 },
});