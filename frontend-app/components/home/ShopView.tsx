import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';

interface ShopViewProps {
  router: any;
  regionId: number | null;
}

export default function ShopView({ router, regionId }: ShopViewProps) {
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const fetchHomeShops = async () => {
      setIsLoading(true);
      try {
        // 동네(regionId)가 필수가 아니도록 수정
        const params: any = { size: 5 };
        if (regionId) {
          params.regionId = regionId;
        }

        const res = await shopApi.getShops(params);
        const shops = res.data?.content || [];
        setShopList(shops);
      } catch (e) {
        console.error('홈 화면 상점 로딩 실패:', e);
      } finally {
        setIsLoading(false);
      }
    };

    fetchHomeShops();
  }, [regionId]);

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
          onPress={() => router.push({
            pathname: '/shop/list' as any,
            params: { regionId: regionId }
          })}
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
            {shopList.map((shop) => {
              const thumbnailUrl = shop.thumbnailUrl || 'https://via.placeholder.com/150/E8F5E9/00A859?text=Store';

              return (
                <TouchableOpacity
                  key={shop.storeId}
                  style={styles.shopCard}
                  onPress={() => router.push(`/shop/${shop.storeId}` as any)}
                >
                  <Image source={{ uri: thumbnailUrl }} style={styles.shopImage} />
                  <Text style={styles.shopName} numberOfLines={1}>{shop.name}</Text>
                  <Text style={styles.shopCategory}>{shop.categoryName || getCategoryName(shop.categoryId)}</Text>
                </TouchableOpacity>
              );
            })}
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