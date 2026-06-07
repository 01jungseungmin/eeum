import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';
import { regionApi } from '../../api/region';

interface ShopViewProps {
  router: any;
}

// ✨ 1. regionId를 props로 받도록 추가합니다.
export default function ShopView({ router }: ShopViewProps) { 
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const fetchHomeData = async () => {
      setIsLoading(true);
      try {
        // 1. 서버에서 대표 지역 ID를 스스로 찾기
        const res = await regionApi.getMyRegions();
        const regions = res.data || [];
        const primary = regions.find((r: any) => r.isPrimary === true);
        
        if (!primary) {
          setShopList([]);
          setIsLoading(false);
          return;
        }

        // 2. 찾은 ID로 상점 조회
        const shopRes = await shopApi.getShops({ size: 5, regionId: primary.regionId }); 
        setShopList(shopRes.data?.content || []); 
      } catch (e) {
        console.error('홈 화면 상점 로딩 실패:', e);
      } finally {
        setIsLoading(false);
      }
    };

    fetchHomeData();
  }, []); // 의존성 배열을 비워두면 됩니다.

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  return (
    <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 30 }}>
      <View style={styles.bannerPlaceholder}>
        <Text style={{ color: '#fff' }}>이벤트 배너 영역</Text>
      </View>
      
      <View style={styles.sectionContainer}>
        <TouchableOpacity 
          style={styles.sectionHeader} 
          onPress={() => router.navigate('/shop/list')}
        >
          <Text style={styles.sectionTitle}>우리 동네 상점</Text>
          <Ionicons name="chevron-forward" size={20} color="#333" />
        </TouchableOpacity>

        {isLoading ? (
          <ActivityIndicator size="small" color="#00A859" style={{ marginTop: 20 }} />
        ) : shopList.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>우리 동네에는 아직 등록된 상점이 없어요.</Text>
          </View>
        ) : (
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {shopList.map((shop) => (
              <TouchableOpacity 
                key={shop.storeId}
                style={styles.shopCard} 
                onPress={() => router.push(`/shop/${shop.storeId}` as any)} 
              >
                <Image source={{ uri: shop.thumbnailUrl || 'https://via.placeholder.com/150/E8F5E9/00A859?text=Store' }} style={styles.shopImage} />
                <Text style={styles.shopName} numberOfLines={1}>{shop.name}</Text>
                <Text style={styles.shopCategory}>{shop.categoryName || getCategoryName(shop.categoryId)}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  bannerPlaceholder: { height: 180, backgroundColor: '#386641', justifyContent: 'center', alignItems: 'center', marginHorizontal: 20, borderRadius: 8, marginBottom: 25 },
  sectionContainer: { paddingLeft: 20, marginBottom: 30 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', paddingRight: 20, alignItems: 'center', marginBottom: 15 },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  shopCard: { marginRight: 15, width: 120 },
  shopImage: { width: 120, height: 120, borderRadius: 8, marginBottom: 8 },
  shopName: { fontSize: 15, fontWeight: '600', color: '#333', marginBottom: 2 },
  shopCategory: { fontSize: 12, color: '#888' },
  emptyState: { paddingVertical: 30, alignItems: 'center', paddingRight: 20 },
  emptyText: { color: '#888', fontSize: 14 }
});