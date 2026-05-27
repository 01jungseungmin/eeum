import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, ActivityIndicator } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SHOP_CATEGORIES, DUMMY_SHOPS } from '../../constants/shopDummyData';
// import { shopApi } from '../../api/shop';

const { width } = Dimensions.get('window');

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  const [isLoading, setIsLoading] = useState(false);

  const shopIdNum = typeof id === 'string' ? Number(id) : 1;
  const foundShop = DUMMY_SHOPS.find(s => s.id === shopIdNum) || DUMMY_SHOPS[0];
  const [shopDetail, setShopDetail] = useState<any>(foundShop);

  /* 🚧 [백엔드 연동 시 주석 해제] 
  useEffect(() => {
    if (id) {
      const fetchShopDetail = async () => {
        setIsLoading(true);
        try {
          const res = await shopApi.getShopDetail(id as string);
          setShopDetail(res.data);
        } catch (e) {
          console.error('상점 상세 데이터 요청 실패:', e);
        } finally {
          setIsLoading(false);
        }
      };
      fetchShopDetail();
    }
  }, [id]);
  */

  if (isLoading) {
    return <View style={{flex:1, justifyContent:'center', alignItems:'center'}}><ActivityIndicator size="large" color="#00A859" /></View>;
  }

  const categoryName = SHOP_CATEGORIES.find(c => c.id === shopDetail.categoryId)?.name || '기타';
  const coverImageUrl = shopDetail.products?.[0]?.imageUrl || 'https://via.placeholder.com/600x400/E8F5E9/00A859?text=Cover';

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.coverContainer}>
          <Image source={{ uri: coverImageUrl }} style={styles.coverImg} />
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}><Ionicons name="chevron-back" size={28} color="#fff" /></TouchableOpacity>
        </View>

        <View style={styles.mainInfo}>
          <View style={styles.categoryBadge}><Text style={styles.categoryText}>{categoryName}</Text></View>
          <View style={styles.nameRow}>
            <Text fontWeight="bold" style={styles.shopName}>{shopDetail.name}</Text>
            <View style={styles.ratingRow}>
              <Ionicons name="star" size={18} color="#FFD700" />
              <Text fontWeight="bold" style={styles.ratingText}>{shopDetail.rating.toFixed(1)}</Text>
              <Text style={styles.reviewCount}>({shopDetail.reviewCount})</Text>
            </View>
          </View>
          
          <View style={styles.contactRow}><Ionicons name="location-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.address}</Text></View>
          <View style={styles.contactRow}><Ionicons name="call-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.phone}</Text></View>
          {shopDetail.businessHours ? (
             <View style={styles.contactRow}><Ionicons name="time-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.businessHours}</Text></View>
          ) : null}
          <View style={styles.contactRow}><Ionicons name="information-circle-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.description}</Text></View>
        </View>

        <View style={styles.divider} />

        <View style={styles.menuSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>메뉴</Text>
          {shopDetail.products?.map((menu: any) => (
            <TouchableOpacity 
              key={menu.id} 
              style={styles.menuCard}
              onPress={() => router.push(`/product/${menu.id}`)}
            >
              <View style={styles.menuTextContainer}>
                {menu.type === 'RESERVATION' && <View style={[styles.popularBadge, {backgroundColor: '#2196F3'}]}><Text style={styles.popularText}>예약상품</Text></View>}
                {menu.status === 'SOLD_OUT' && <View style={[styles.popularBadge, {backgroundColor: '#999'}]}><Text style={styles.popularText}>품절</Text></View>}
                <Text fontWeight="bold" style={styles.menuName}>{menu.name}</Text>
                <Text style={styles.menuDesc} numberOfLines={2}>{menu.description}</Text>
                
                {/* 할인가가 있으면 취소선 표시 */}
                {menu.eventPrice ? (
                  <View style={{flexDirection: 'row', alignItems: 'center'}}>
                    <Text fontWeight="bold" style={[styles.menuPrice, { color: '#FF5252' }]}>{menu.eventPrice.toLocaleString()}원</Text>
                    <Text style={{textDecorationLine: 'line-through', color: '#bbb', marginLeft: 6, fontSize: 13}}>{menu.price.toLocaleString()}원</Text>
                  </View>
                ) : (
                  <Text fontWeight="bold" style={styles.menuPrice}>{menu.price.toLocaleString()}원</Text>
                )}
              </View>
              <Image source={{ uri: menu.imageUrl }} style={styles.menuImg} />
            </TouchableOpacity>
          ))}
          {(!shopDetail.products || shopDetail.products.length === 0) && (
             <Text style={{color: '#999', marginTop: 10}}>등록된 메뉴가 없습니다.</Text>
          )}
        </View>

        <View style={styles.divider} />
        
        <View style={styles.reviewPreview}>
          <View style={styles.sectionHeader}>
            <Text fontWeight="bold" style={styles.sectionTitle}>리뷰 {shopDetail.reviewCount}</Text>
            <Ionicons name="chevron-forward" size={20} color="#999" />
          </View>
          <Text style={styles.reviewPlaceholder}>리뷰를 확인하고 별점을 남겨보세요!</Text>
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.inquiryBtn}><Text fontWeight="bold" style={styles.inquiryBtnText}>사장님께 문의하기</Text></TouchableOpacity>
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
  reviewCount: { color: '#999', marginLeft: 5 },
  contactRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  contactText: { fontSize: 14, color: '#666', marginLeft: 10 },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  menuSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 20 },
  menuCard: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  menuTextContainer: { flex: 1, paddingRight: 15 },
  popularBadge: { backgroundColor: '#FF5252', alignSelf: 'flex-start', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginBottom: 5 },
  popularText: { color: '#fff', fontSize: 10, fontWeight: 'bold' },
  menuName: { fontSize: 16, color: '#333', marginBottom: 5 },
  menuDesc: { fontSize: 13, color: '#888', marginBottom: 10 },
  menuPrice: { fontSize: 16, color: '#333' },
  menuImg: { width: 100, height: 100, borderRadius: 8 },
  reviewPreview: { padding: 20, paddingBottom: 100 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  reviewPlaceholder: { color: '#999', marginTop: 10 },
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  inquiryBtn: { backgroundColor: '#00A859', paddingVertical: 15, borderRadius: 8, alignItems: 'center' },
  inquiryBtnText: { color: '#fff', fontSize: 16 }
});