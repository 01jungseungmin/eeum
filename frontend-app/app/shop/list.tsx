import React, { useState, useEffect, useRef } from 'react';
import { View, StyleSheet, FlatList, Image, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import { useRouter, useLocalSearchParams } from 'expo-router'; 
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

  const categoryListRef = useRef<FlatList<any>>(null);

  const [hasRegion, setHasRegion] = useState(false);
  
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

  useEffect(() => {
    const fetchShopsByPrimaryRegion = async () => {
      setIsLoading(true);
      try {
        // 1. 내 동네 목록 전체 조회
        const res = await regionApi.getMyRegions();
        
        // 2. 서버 응답 구조 확인 (res.data에 배열이 들어있는지 확인)
        // 스웨거 예시를 보면 success: true, data: [...] 구조일 수 있습니다.
        const regions = res.data || []; 
        
        // 3. isPrimary가 true인 놈을 찾기!
        const primary = regions.find((r: any) => r.isPrimary === true);

        if (primary) {
          setHasRegion(true); // ✨ 동네가 있음을 알림
          const shopRes = await shopApi.getShops({ regionId: primary.regionId, size: 20 });
          setShopList(shopRes.data?.content || []);
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
  }, [selectedCategoryId]);

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

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

 const renderShopCard = ({ item }: any) => {
  const thumbnailUrl = item.thumbnailUrl || 'https://via.placeholder.com/300/E8F5E9/00A859?text=Store';

    return (
      <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.storeId}`)}>
        <Image source={{ uri: thumbnailUrl }} style={styles.cardImage} />
        <View style={styles.cardTitleRow}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
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
        <View style={styles.footerRow}>
          <View style={styles.footerItem}>
            <Ionicons name="heart" size={12} color="#999" />
            <Text style={styles.footerText}>{item.favoriteCount || 0}</Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 1. 상단 헤더 (항상 고정) */}
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
        <TouchableOpacity style={styles.sortButton}>
          <Text style={styles.sortText}>최신순</Text>
          <Ionicons name="chevron-down" size={14} color="#666" />
        </TouchableOpacity>
      </View>

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
  footerRow: { flexDirection: 'row', alignItems: 'center' },
  footerItem: { flexDirection: 'row', alignItems: 'center', marginRight: 10 },
  footerText: { fontSize: 11, color: '#999', marginLeft: 4 },
});