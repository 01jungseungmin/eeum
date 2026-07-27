import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';

interface ShopViewProps {
  router: any;
  regionId?: number | null;
}

export default function ShopView({ router, regionId }: ShopViewProps) {
  const [shopList, setShopList] = useState<any[]>([]);
  // 이벤트 상품 상태
  const [eventProducts, setEventProducts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [hasNoRegion, setHasNoRegion] = useState(false);

  useEffect(() => {
    const fetchHomeData = async () => {
      if (!regionId) {
        setHasNoRegion(true);
        setShopList([]);
        setEventProducts([]);
        return;
      }

      setIsLoading(true);
      setHasNoRegion(false);
      try {
        const shopRes = await shopApi.getShops({ size: 15, regionId: regionId }); 
        const fetchedShops = shopRes.data?.content || shopRes.data || [];
        setShopList(fetchedShops); 

        // 이벤트 상품 가져오기
        if (fetchedShops.length > 0) {
          const eventPromises = fetchedShops.map((shop: any) => 
            shopApi.getEventProducts(shop.storeId).catch(() => null)
          );
          
          const eventResponses = await Promise.all(eventPromises);
          let allEvents: any[] = [];
          
          eventResponses.forEach((res: any, index: number) => {
            if (res && res.data && res.data.length > 0) {
              const productsWithStoreInfo = res.data.map((product: any) => ({
                ...product,
                storeId: fetchedShops[index].storeId,
                storeName: fetchedShops[index].name
              }));
              allEvents = [...allEvents, ...productsWithStoreInfo];
            }
          });

          // 진행 중이고 재고가 있는 상품만 필터링
          const activeEvents = allEvents.filter(p => p.ongoing && p.remainingStock > 0);
          setEventProducts(activeEvents);
        } else {
          setEventProducts([]);
        }
      } catch (e) {
        console.error('홈 화면 데이터 로딩 실패:', e);
        setShopList([]);
        setEventProducts([]);
      } finally {
        setIsLoading(false);
      }
    };

    fetchHomeData();
  }, [regionId]);

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  return (
    <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 30 }}>
      <View style={styles.bannerPlaceholder}>
        <Text style={{ color: '#fff' }}>이벤트 배너</Text>
      </View>

      {/* 1. 우리 동네 상점 영역 */}
      <View style={styles.sectionContainer}>
        <TouchableOpacity
          style={styles.sectionHeader}
          onPress={() => router.push({ pathname: '/shop/list' as any, params: { regionId: regionId } })}
        >
          <Text style={styles.sectionTitle}>우리 동네 상점</Text>
          <Ionicons name="chevron-forward" size={20} color="#333" />
        </TouchableOpacity>

        {isLoading ? (
          <ActivityIndicator size="small" color="#00A859" style={{ marginTop: 20 }} />
        ) : hasNoRegion ? (
          <View style={styles.emptyState}><Text style={styles.emptyText}>먼저 상단에서 동네를 설정해주세요!</Text></View>
        ) : shopList.length === 0 ? (
          <View style={styles.emptyState}><Text style={styles.emptyText}>우리 동네에는 아직 등록된 상점이 없어요.</Text></View>
        ) : (
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {shopList.map((shop) => (
              <TouchableOpacity
                key={`shop-${shop.storeId}`}
                style={styles.shopCard}
                onPress={() => router.push(`/shop/${shop.storeId}` as any)}
              >
                <Image source={{ uri: shop.thumbnailUrl || 'https://via.placeholder.com/150/F0F0F0/CCCCCC' }} style={styles.shopImage} />
                <Text style={styles.shopCategory}>{shop.categoryName || getCategoryName(shop.categoryId)}</Text>
                <Text style={styles.shopName} numberOfLines={1}>{shop.name}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}
      </View>

      {/* 2. 이벤트 진행 중인 상점 (상품 데이터 포함) */}
      {!isLoading && eventProducts.length > 0 && (
        <View style={styles.sectionContainer}>
          <TouchableOpacity 
            style={styles.sectionHeader}
            // 화살표 터치 시 새로 만들 리스트 페이지로 이동
            onPress={() => router.push({ pathname: '/shop/event-list' as any, params: { regionId: regionId } })}
          >
            <Text style={styles.sectionTitle}>이벤트 진행 중인 상점</Text>
            <Ionicons name="chevron-forward" size={20} color="#333" />
          </TouchableOpacity>
          
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {eventProducts.map((product) => (
              <TouchableOpacity
                key={`event-${product.eventProductId}`}
                style={styles.shopCard}
                onPress={() => router.push(`/shop/${product.storeId}` as any)}
              >
                <View style={styles.eventImageContainer}>
                  <Image source={{ uri: product.thumbnailUrl || 'https://via.placeholder.com/150/F0F0F0/CCCCCC' }} style={styles.shopImage} />
                  <View style={styles.stockBadge}>
                    <Text style={styles.stockText}>잔여 {product.remainingStock}개</Text>
                  </View>
                </View>
                
                {/* 사진 형식대로 카테고리(상점이름) -> 메인이름(상품이름) 순서 배치 */}
                <Text style={styles.shopCategory} numberOfLines={1}>{product.storeName}</Text>
                <Text style={styles.shopName} numberOfLines={1}>{product.productName}</Text>
                
                <View style={styles.priceContainer}>
                  <Text style={styles.originalPrice}>{product.originalPrice.toLocaleString()}원</Text>
                  <Text style={styles.eventPrice}>{product.eventPrice.toLocaleString()}원</Text>
                </View>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  bannerPlaceholder: { height: 180, backgroundColor: '#386641', justifyContent: 'center', alignItems: 'center', marginHorizontal: 20, borderRadius: 8, marginBottom: 25 },
  sectionContainer: { paddingLeft: 20, marginBottom: 35 }, 
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', paddingRight: 20, alignItems: 'center', marginBottom: 15 },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  
  // 통합된 카드 스타일
  shopCard: { marginRight: 15, width: 140 }, 
  shopImage: { width: 140, height: 140, borderRadius: 8, marginBottom: 10, backgroundColor: '#F0F0F0' },
  shopCategory: { fontSize: 12, color: '#888', marginBottom: 4 }, 
  shopName: { fontSize: 15, fontWeight: 'bold', color: '#333', marginBottom: 4 }, 
  
  // 이벤트 상품 전용 스타일
  eventImageContainer: { position: 'relative' },
  stockBadge: { position: 'absolute', top: 8, left: 8, backgroundColor: 'rgba(231, 76, 60, 0.9)', paddingHorizontal: 6, paddingVertical: 3, borderRadius: 4 },
  stockText: { color: '#fff', fontSize: 11, fontWeight: 'bold' },
  priceContainer: { flexDirection: 'row', alignItems: 'center' },
  originalPrice: { fontSize: 12, color: '#999', textDecorationLine: 'line-through', marginRight: 6 },
  eventPrice: { fontSize: 14, fontWeight: 'bold', color: '#E74C3C' },

  emptyState: { paddingVertical: 30, alignItems: 'center', paddingRight: 20 },
  emptyText: { color: '#888', fontSize: 14 },
});