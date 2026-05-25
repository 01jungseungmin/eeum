import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, ActivityIndicator } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

// 🚧 [백엔드 연동]
// import { shopApi } from '../../api/shop';

const { width } = Dimensions.get('window');

const MOCK_SHOPS: { [key: string]: any } = {
  '1': { name: '곱도리 식당', category: '식당', rating: 4.9, reviewCount: 342, address: '서울시 강서구 등촌제2동 123-45', phone: '02-1234-5678', hours: '매일 11:00 - 22:00 (라스트오더 21:30)', coverImg: 'https://via.placeholder.com/600x400/E8F5E9/00A859?text=Gobdori', menus: [{ id: 'm1', name: '곱도리탕 (2인)', price: 28000, desc: '매콤한 닭볶음탕과 고소한 대창', isPopular: true, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Menu1' }, { id: 'm2', name: '김치찌개', price: 9000, desc: '직접 담근 묵은지로 끓인 시원한 맛', isPopular: true, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Menu2' }] },
  '2': { name: '베이커리 밀크빵', category: '베이커리', rating: 4.8, reviewCount: 156, address: '서울시 강서구 등촌제1동 67-89', phone: '02-9876-5432', hours: '매일 08:00 - 21:00', coverImg: 'https://via.placeholder.com/600x400/FFF3E0/FF9800?text=Bakery', menus: [{ id: 'm3', name: '순수 우유 식빵', price: 4500, desc: '100% 우유로 반죽한 식빵', isPopular: true, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Bread1' }, { id: 'm4', name: '소금빵', price: 2500, desc: '버터 풍미 가득 소금빵', isPopular: true, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Bread2' }] },
  '3': { name: '카페 라떼하우스', category: '카페', rating: 4.7, reviewCount: 92, address: '서울시 강서구 등촌제1동 11-22', phone: '02-3333-4444', hours: '매일 09:00 - 22:00', coverImg: 'https://via.placeholder.com/600x400/EFEBE9/795548?text=Cafe', menus: [{ id: 'm5', name: '아이스 카페라떼', price: 4500, desc: '고소한 원두와 부드러운 우유', isPopular: true, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Latte' }] },
  '4': { name: '우리동네 싱싱마트', category: '마트', rating: 4.9, reviewCount: 512, address: '서울시 강서구 등촌제2동 33-44', phone: '02-5555-6666', hours: '매일 08:00 - 23:00', coverImg: 'https://via.placeholder.com/600x400/E3F2FD/2196F3?text=Mart', menus: [{ id: 'm7', name: '제철 과일 모듬', price: 15000, desc: '당도 높은 제철 과일 패키지', isPopular: true, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Fruit' }] },
  '5': { name: '돈까스 마스터', category: '식당', rating: 4.6, reviewCount: 210, address: '서울시 강서구 등촌제1동 55-66', phone: '02-7777-8888', hours: '매일 11:30 - 21:00', coverImg: 'https://via.placeholder.com/600x400/FFEBEE/F44336?text=Cutlet', menus: [{ id: 'm8', name: '수제 등심 돈까스', price: 11000, desc: '국내산 한돈 겉바속촉 돈까스', isPopular: true, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Pork' }] }
};

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); 
  const [isLoading, setIsLoading] = useState(false);

  const shopId = typeof id === 'string' ? id : '1';
  // ✨ 현재는 더미 데이터 매핑 상태
  const [shopDetail, setShopDetail] = useState<any>(MOCK_SHOPS[shopId] || MOCK_SHOPS['1']);

  /* 🚧 [백엔드 연동 켜기] 실제 백엔드 연동 시 아래 주석을 해제하세요!
  useEffect(() => {
    if (id) {
      const fetchShopDetail = async () => {
        setIsLoading(true);
        try {
          const res = await shopApi.getShopDetail(id as string);
          setShopDetail(res.data); // 서버에서 넘어온 단건 가게 상세 정보로 State 갱신
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

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.coverContainer}>
          <Image source={{ uri: shopDetail.coverImg }} style={styles.coverImg} />
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}><Ionicons name="chevron-back" size={28} color="#fff" /></TouchableOpacity>
        </View>

        <View style={styles.mainInfo}>
          <View style={styles.categoryBadge}><Text style={styles.categoryText}>{shopDetail.category}</Text></View>
          <View style={styles.nameRow}>
            <Text fontWeight="bold" style={styles.shopName}>{shopDetail.name}</Text>
            <View style={styles.ratingRow}>
              <Ionicons name="star" size={18} color="#FFD700" />
              <Text fontWeight="bold" style={styles.ratingText}>{shopDetail.rating}</Text>
              <Text style={styles.reviewCount}>({shopDetail.reviewCount})</Text>
            </View>
          </View>
          
          <View style={styles.contactRow}><Ionicons name="location-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.address}</Text></View>
          <View style={styles.contactRow}><Ionicons name="call-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.phone}</Text></View>
          <View style={styles.contactRow}><Ionicons name="time-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.hours}</Text></View>
        </View>

        <View style={styles.divider} />

        <View style={styles.menuSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>메뉴</Text>
          {shopDetail.menus.map((menu: any) => (
            <TouchableOpacity 
              key={menu.id} 
              style={styles.menuCard}
              onPress={() => router.push(`/product/${menu.id}`)} // ✨ 상품 상세이동 연결 완료
            >
              <View style={styles.menuTextContainer}>
                {menu.isPopular && <View style={styles.popularBadge}><Text style={styles.popularText}>인기</Text></View>}
                <Text fontWeight="bold" style={styles.menuName}>{menu.name}</Text>
                <Text style={styles.menuDesc} numberOfLines={2}>{menu.desc}</Text>
                <Text fontWeight="bold" style={styles.menuPrice}>{menu.price.toLocaleString()}원</Text>
              </View>
              <Image source={{ uri: menu.img }} style={styles.menuImg} />
            </TouchableOpacity>
          ))}
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