import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, ActivityIndicator, Linking, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SHOP_CATEGORIES, DUMMY_SHOPS } from '../../constants/shopDummyData';

const { width } = Dimensions.get('window');

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  const [isLoading, setIsLoading] = useState(false);

  const shopIdNum = typeof id === 'string' ? Number(id) : 1;
  const foundShop = DUMMY_SHOPS.find(s => s.id === shopIdNum) || DUMMY_SHOPS[0];
  const [shopDetail, setShopDetail] = useState<any>(foundShop);

  if (isLoading) return <View style={{flex:1, justifyContent:'center', alignItems:'center'}}><ActivityIndicator size="large" color="#00A859" /></View>;

  const categoryName = SHOP_CATEGORIES.find(c => c.id === shopDetail.categoryId)?.name || '기타';
  const coverImageUrl = shopDetail.products?.[0]?.imageUrl || 'https://via.placeholder.com/600x400/E8F5E9/00A859?text=Cover';

  // 💡 [핵심] 카테고리 1(음식점), 2(카페)는 네이버 지도(식당) 스타일로!
  const isRestaurant = shopDetail.categoryId === 1 || shopDetail.categoryId === 2;

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 100 }}>
        {/* 상단 이미지 ~ 메인 정보 생략 없이 기존 코드 그대로 유지 */}
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
            </View>
          </View>
          <View style={styles.contactRow}><Ionicons name="location-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.address}</Text></View>
          <View style={styles.contactRow}><Ionicons name="call-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.phone}</Text></View>
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
                <Text fontWeight="bold" style={styles.menuName}>{menu.name}</Text>
                <Text style={styles.menuDesc} numberOfLines={2}>{menu.description}</Text>
                <Text fontWeight="bold" style={styles.menuPrice}>{menu.price.toLocaleString()}원</Text>
              </View>
              <Image source={{ uri: menu.imageUrl }} style={styles.menuImg} />
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>

      {/* 💡 [네이버 지도 vs 쿠팡] 가게 상세 하단 바 */}
      <View style={styles.bottomBar}>
        {isRestaurant ? (
          <>
            <TouchableOpacity style={styles.callBtn} onPress={() => Linking.openURL(`tel:${shopDetail.phone}`)}>
              <Ionicons name="call" size={18} color="#00A859" style={{marginRight: 6}} />
              <Text fontWeight="bold" style={styles.callBtnText}>전화하기</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.reserveBtn} onPress={() => Alert.alert('방문 예약', '예약 페이지로 이동합니다.')}>
              <Text fontWeight="bold" style={styles.reserveBtnText}>방문 예약하기</Text>
            </TouchableOpacity>
          </>
        ) : (
          <TouchableOpacity style={styles.inquiryBtn}>
            <Text fontWeight="bold" style={styles.inquiryBtnText}>사장님께 문의하기</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  // 기존 스타일 유지...
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
  
  // 💡 하단 바 스타일 개선 (레이아웃 깨짐 방지)
  bottomBar: { flexDirection: 'row', padding: 15, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  
  // 식당(네이버 지도) 버튼
  callBtn: { flex: 1, flexDirection: 'row', backgroundColor: '#fff', borderWidth: 1, borderColor: '#00A859', paddingVertical: 15, borderRadius: 8, alignItems: 'center', justifyContent: 'center', marginRight: 10 },
  callBtnText: { color: '#00A859', fontSize: 16 },
  reserveBtn: { flex: 2, backgroundColor: '#00A859', paddingVertical: 15, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  reserveBtnText: { color: '#fff', fontSize: 16 },
  
  // 상점(쿠팡) 버튼
  inquiryBtn: { flex: 1, backgroundColor: '#00A859', paddingVertical: 15, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  inquiryBtnText: { color: '#fff', fontSize: 16 }
});