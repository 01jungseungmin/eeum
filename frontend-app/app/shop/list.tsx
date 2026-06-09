import React, { useState, useEffect, useRef } from 'react';
import { View, StyleSheet, FlatList, Image, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router'; 
import { Text } from '../../components/CustomText';

import { regionApi } from '../../api/region';
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';

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

  // ✨ favorite 브랜치와 100% 동일한 동네 기반 조회 로직
  useEffect(() => {
    const fetchShopsByPrimaryRegion = async () => {
      setIsLoading(true);
      try {
        const res = await regionApi.getMyRegions();
        const regions = res.data || []; 
        const primary = regions.find((r: any) => r.isPrimary === true);

        if (primary) {
          setHasRegion(true);
          // 카테고리 필터링 적용 (0이면 전체)
          const categoryParam = selectedCategoryId === 0 ? undefined : selectedCategoryId;
          
          const shopRes = await shopApi.getShops({ 
            regionId: primary.regionId, 
            categoryId: categoryParam, // ✨ 카테고리 선택 시 필터링 반영
            size: 20 
          });
          
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

  const renderShopCard = ({ item }: any) => {
    const thumbnailUrl = item.thumbnailUrl || 'https://via.placeholder.com/300/E8F5E9/00A859?text=Store';

    return (
      <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.storeId}`)}>
        <Image source={{ uri: thumbnailUrl }} style={styles.cardImage} />
        <View style={styles.cardTitleRow}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
          {/* 🗑️ 찜(하트) 아이콘 영역 삭제 */}
        </View>
        <View style={styles.ratingRow}>
          <Ionicons name="star" size={14} color="#FFD700" />
          <Text fontWeight="bold" style={styles.ratingText}>{item.rating?.toFixed(1) || '0.0'}</Text>
          <Text style={styles.reviewText}>({item.reviewCount || 0})</Text>
        </View>
        <Text style={styles.locationText}>{item.categoryName || getCategoryName(item.categoryId)}</Text>
        {/* 🗑️ 하단 찜 개수 카운트 영역 삭제 */}
      </TouchableOpacity>
    );
  };

  // ✨ favorite 브랜치와 100% 동일한 UI 레이아웃 구조 (하얀 화면 에러 방지)
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

      {/* 4. 메인 상점 리스트 (조건부 렌더링 구조 복구) */}
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