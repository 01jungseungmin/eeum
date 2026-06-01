import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';

export default function ShopView({ router }: { router: any }) {
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  // 백엔드 API 연동 완료
  useEffect(() => {
    const fetchHomeShops = async () => {
      setIsLoading(true);
      try {
        // 홈 화면이므로 앞의 5개만 가져오도록 size 파라미터 전달
        const res = await shopApi.getShops({ size: 5 }); 
        
        // 💡 [핵심] 백엔드가 페이징 객체로 주므로 data.content를 뽑아냅니다!
        const shops = res.data?.content || [];
        setShopList(shops); 
      } catch (e) {
        console.error('홈 화면 상점 로딩 실패:', e);
      } finally {
        setIsLoading(false);
      }
    };
    fetchHomeShops();
  }, []);

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
          onPress={() => router.push('/shop/list')}
        >
          <Text style={styles.sectionTitle}>우리 동네 상점</Text>
          <Ionicons name="chevron-forward" size={20} color="#333" />
        </TouchableOpacity>

        {isLoading ? (
          <ActivityIndicator size="small" color="#00A859" style={{ marginTop: 20 }} />
        ) : (
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {shopList.map((shop) => {
              // 서버가 주는 thumbnailUrl 바로 사용
              const thumbnailUrl = shop.thumbnailUrl || 'https://via.placeholder.com/150/E8F5E9/00A859?text=Store';
              
              return (
                <TouchableOpacity 
                  key={shop.storeId}
                  style={styles.shopCard} 
                  onPress={() => router.push(`/shop/${shop.storeId}`)} 
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
});