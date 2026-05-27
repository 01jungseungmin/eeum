import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

// 💡 작성하신 더미 데이터 불러오기
import { DUMMY_SHOPS, SHOP_CATEGORIES } from '../../constants/shopDummyData';
// 🚧 [백엔드 연동]
// import { shopApi } from '../../api/shop';

export default function ShopView({ router }: { router: any }) {
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  // [더미 데이터 로직] 홈 화면이므로 전체 상점 중 앞의 5개만 미리보기로 띄웁니다.
  useEffect(() => {
    setShopList((DUMMY_SHOPS || []).slice(0, 5));
  }, []);

  /* 🚧 [백엔드 API 연동 시 주석 해제]
  useEffect(() => {
    const fetchHomeShops = async () => {
      setIsLoading(true);
      try {
        // 홈 화면용 추천 상점 목록을 불러오는 API (예시)
        const res = await shopApi.getShops(); 
        setShopList(res.data.slice(0, 5)); 
      } catch (e) {
        console.error('홈 화면 상점 로딩 실패:', e);
      } finally {
        setIsLoading(false);
      }
    };
    fetchHomeShops();
  }, []);
  */

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
              // 썸네일은 첫 번째 상품의 이미지를 가져오거나, 없으면 기본 이미지 사용
              const thumbnailUrl = shop.products?.[0]?.imageUrl || 'https://via.placeholder.com/150/E8F5E9/00A859?text=Store';
              
              return (
                <TouchableOpacity 
                  key={shop.id} 
                  style={styles.shopCard} 
                  onPress={() => router.push(`/shop/${shop.id}`)}
                >
                  <Image source={{ uri: thumbnailUrl }} style={styles.shopImage} />
                  <Text style={styles.shopName} numberOfLines={1}>{shop.name}</Text>
                  <Text style={styles.shopCategory}>{getCategoryName(shop.categoryId)}</Text>
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