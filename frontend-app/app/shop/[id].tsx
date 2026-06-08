import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { shopApi } from '../../api/shop';

const { width } = Dimensions.get('window');

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  
  const [isLoading, setIsLoading] = useState(true);
  const [shopDetail, setShopDetail] = useState<any>(null);
  const [shopProducts, setShopProducts] = useState<any[]>([]);

  const shopIdNum = typeof id === 'string' ? Number(id) : 1;

  useEffect(() => {
    const fetchShopData = async () => {
      try {
        setIsLoading(true);
        const [detailData, productsData] = await Promise.all([
          shopApi.getShopDetail(shopIdNum),
          shopApi.getShopProducts(shopIdNum)
        ]);
        
        setShopDetail(detailData);
        setShopProducts(productsData || []);
      } catch (e) {
        Alert.alert("오류", "상점 정보를 불러오지 못했습니다.");
        router.back();
      } finally {
        setIsLoading(false);
      }
    };
    
    if (shopIdNum) fetchShopData();
  }, [shopIdNum]);

  if (isLoading || !shopDetail) {
    return (
      <View style={{flex:1, justifyContent:'center', alignItems:'center', backgroundColor: '#fff'}}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  const categoryName = shopDetail.categoryName || '기타';
  const coverImageUrl = shopDetail.images?.[0]?.imageUrl || 'https://via.placeholder.com/600x400/E8F5E9/00A859?text=Cover';
  
  // ✨ 백엔드에서 주는 카테고리 ID를 바탕으로 식당/상점 구분 (1: 음식점, 2: 카페)
  const isRestaurant = shopDetail.categoryId === 1 || shopDetail.categoryId === 2;

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 150 }}>
        
        {/* 커버 이미지 */}
        <View style={styles.coverContainer}>
          <Image source={{ uri: coverImageUrl }} style={styles.coverImg} />
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}>
            <Ionicons name="chevron-back" size={28} color="#fff" />
          </TouchableOpacity>
        </View>

        {/* 메인 정보 */}
        <View style={styles.mainInfo}>
          <View style={styles.categoryBadge}><Text style={styles.categoryText}>{categoryName}</Text></View>
          <View style={styles.nameRow}>
            <Text fontWeight="bold" style={styles.shopName}>{shopDetail.name}</Text>
            <View style={styles.ratingRow}>
              <Ionicons name="star" size={18} color="#FFD700" />
              <Text fontWeight="bold" style={styles.ratingText}>{shopDetail.rating?.toFixed(1) || '0.0'}</Text>
            </View>
          </View>
          <View style={styles.contactRow}><Ionicons name="location-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.address}</Text></View>
          <View style={styles.contactRow}><Ionicons name="call-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.phone}</Text></View>
        </View>

        <View style={styles.divider} />

        {/* 메뉴 섹션 */}
        <View style={styles.menuSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>메뉴</Text>
          {shopProducts.map((menu: any) => (
            <TouchableOpacity 
              key={menu.productId}
              style={styles.menuCard}
              onPress={() => router.push(`/product/${menu.productId}`)}
            >
              <View style={styles.menuTextContainer}>
                <Text fontWeight="bold" style={styles.menuName}>{menu.name}</Text>
                <Text style={styles.menuDesc} numberOfLines={2}>{menu.description}</Text>
                {menu.hasEvent ? (
                  <View style={{flexDirection: 'row', alignItems: 'center'}}>
                    <Text style={{textDecorationLine: 'line-through', color: '#bbb', marginRight: 6, fontSize: 13}}>
                      {menu.price?.toLocaleString()}원
                    </Text>
                    <Text fontWeight="bold" style={[styles.menuPrice, {color: '#FF5252'}]}>
                      {menu.eventPrice?.toLocaleString()}원
                    </Text>
                  </View>
                ) : (
                  <Text fontWeight="bold" style={styles.menuPrice}>{menu.price?.toLocaleString()}원</Text>
                )}
              </View>
              {menu.thumbnailUrl && <Image source={{ uri: menu.thumbnailUrl }} style={styles.menuImg} />}
            </TouchableOpacity>
          ))}
          {shopProducts.length === 0 && (
            <Text style={{ color: '#888', marginTop: 10 }}>등록된 메뉴가 없습니다.</Text>
          )}
        </View>

      </ScrollView>

      {/* 하단 버튼 영역 */}
      <View style={styles.bottomBar}>
        {isRestaurant ? (
          <View style={{ width: '100%', gap: 10 }}>
            {/* 식당용 버튼 1 - 방문 예약 */}
            <TouchableOpacity 
              style={styles.primaryBtn} 
              onPress={() => router.push({
                pathname: '/restaurant/reservation' as any,
                params: { storeId: shopDetail.storeId }
              })}
            >
              <Text fontWeight="bold" style={styles.primaryBtnText}>방문 예약하기</Text>
            </TouchableOpacity>

            {/* 식당용 버튼 2 - 사장님과 채팅 (위아래로 꽉 찬 디자인) */}
            <TouchableOpacity 
              style={styles.chatBtn} 
              onPress={() => Alert.alert('안내', '채팅 기능은 준비 중입니다.')}
            >
              <Text style={styles.chatBtnText}>사장님과 채팅</Text>
            </TouchableOpacity>
          </View>
        ) : (
          /* 일반 상점용 장바구니 버튼 (유지) */
          <TouchableOpacity 
            style={styles.primaryBtn}
            onPress={() => router.push('/cart')}
          >
            <Text fontWeight="bold" style={styles.primaryBtnText}>장바구니 보기</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  coverContainer: { width: '100%', height: 250, position: 'relative' },
  coverImg: { width: '100%', height: '100%' },
  backBtn: { position: 'absolute', top: 20, left: 20, backgroundColor: 'rgba(0,0,0,0.3)', padding: 8, borderRadius: 20 },
  mainInfo: { padding: 20 },
  categoryBadge: { backgroundColor: '#E8F5E9', alignSelf: 'flex-start', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 4, marginBottom: 10 },
  categoryText: { color: '#00A859', fontSize: 12 },
  nameRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 15 },
  shopName: { fontSize: 24, color: '#333' },
  ratingRow: { flexDirection: 'row', alignItems: 'center' },
  ratingText: { fontSize: 18, color: '#333', marginLeft: 5 },
  contactRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  contactText: { fontSize: 14, color: '#666', marginLeft: 10 },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  
  menuSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 20 },
  menuCard: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  menuTextContainer: { flex: 1, paddingRight: 15 },
  menuName: { fontSize: 16, color: '#333', marginBottom: 5 },
  menuDesc: { fontSize: 13, color: '#888', marginBottom: 10 },
  menuPrice: { fontSize: 16, color: '#333' },
  menuImg: { width: 100, height: 100, borderRadius: 8 },
  
  /* 버튼 영역 스타일 */
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  primaryBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  primaryBtnText: { color: '#fff', fontSize: 16 },
  
  /* 사장님과 채팅 버튼 스타일 (풀사이즈) */
  chatBtn: { width: '100%', paddingVertical: 16, borderRadius: 8, borderWidth: 1, borderColor: '#00A859', alignItems: 'center', justifyContent: 'center' },
  chatBtnText: { color: '#00A859', fontSize: 16, fontWeight: 'bold' }
});