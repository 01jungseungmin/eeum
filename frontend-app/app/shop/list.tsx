import React, { useState, useEffect } from 'react';
import { 
  View, 
  StyleSheet, 
  FlatList, 
  Image, 
  TouchableOpacity, 
  ScrollView,
  ActivityIndicator // ✨ 실제 로딩 서버 스피너 표시용
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';

// 🚧 [백엔드 연동] 백엔드가 API를 완성하면 아래 주석을 해제합니다.
// import { shopApi } from '../../api/shop'; 

const CATEGORIES = ['전체', '식당', '카페', '베이커리', '마트'];

const MOCK_SHOP_LIST = [
  { id: '1', name: '곱도리 식당', rating: 4.9, reviewCount: 342, category: '식당', likeCount: 45, viewCount: 89, isLiked: false, thumbnailUrl: 'https://via.placeholder.com/300/E8F5E9/00A859?text=Gobdori' },
  { id: '2', name: '베이커리 밀크빵', rating: 4.8, reviewCount: 156, category: '베이커리', likeCount: 56, viewCount: 123, isLiked: true, thumbnailUrl: 'https://via.placeholder.com/300/FFF3E0/FF9800?text=Bakery' },
  { id: '3', name: '카페 라떼하우스', rating: 4.7, reviewCount: 92, category: '카페', likeCount: 38, viewCount: 74, isLiked: false, thumbnailUrl: 'https://via.placeholder.com/300/EFEBE9/795548?text=Cafe' },
  { id: '4', name: '우리동네 싱싱마트', rating: 4.9, reviewCount: 512, category: '마트', likeCount: 120, viewCount: 340, isLiked: false, thumbnailUrl: 'https://via.placeholder.com/300/E3F2FD/2196F3?text=Mart' },
  { id: '5', name: '돈까스 마스터', rating: 4.6, reviewCount: 210, category: '식당', likeCount: 64, viewCount: 115, isLiked: true, thumbnailUrl: 'https://via.placeholder.com/300/FFEBEE/F44336?text=Cutlet' }
];

export default function ShopListScreen() {
  const router = useRouter();
  const [selectedCategory, setSelectedCategory] = useState('전체');
  const [shopList, setShopList] = useState<any[]>(MOCK_SHOP_LIST);
  const [isLoading, setIsLoading] = useState(false); // ✨ 로딩 상태 관리

  // 💡 [더미 데이터용 필터 로직] - 실제 API 연결 시 이 useEffect는 주석 처리하거나 지웁니다.
  useEffect(() => {
    if (selectedCategory === '전체') {
      setShopList(MOCK_SHOP_LIST);
    } else {
      const filtered = MOCK_SHOP_LIST.filter(shop => shop.category === selectedCategory);
      setShopList(filtered);
    }
  }, [selectedCategory]);

  /* 🚧 [백엔드 연동 켜기] 실제 백엔드 연동 시 위의 useEffect를 지우고 아래 주석을 푸세요!
  useEffect(() => {
    const fetchShopList = async () => {
      setIsLoading(true);
      try {
        // 카테고리가 '전체'일 때는 null을 보내고, 아닐 때는 선택된 카테고리명을 파라미터로 전송
        const categoryParam = selectedCategory === '전체' ? null : selectedCategory;
        const res = await shopApi.getShops(categoryParam);
        setShopList(res.data); // 백엔드 데이터로 교체
      } catch (e) {
        console.error('상점 목록 API 로딩 실패:', e);
      } finally {
        setIsLoading(false);
      }
    };
    fetchShopList();
  }, [selectedCategory]);
  */

  const renderShopCard = ({ item }: any) => (
    <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.id}`)}>
      <Image source={{ uri: item.thumbnailUrl }} style={styles.cardImage} />
      <View style={styles.cardTitleRow}>
        <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
        <Ionicons name={item.isLiked ? "heart" : "heart-outline"} size={20} color={item.isLiked ? "#FF5252" : "#999"} />
      </View>
      <View style={styles.ratingRow}>
        <Ionicons name="star" size={14} color="#FFD700" />
        <Text fontWeight="bold" style={styles.ratingText}>{item.rating}</Text>
        <Text style={styles.reviewText}>({item.reviewCount})</Text>
      </View>
      <Text style={styles.locationText}>{item.category}</Text>
      <View style={styles.footerRow}>
        <View style={styles.footerItem}><Ionicons name="heart" size={12} color="#999" /><Text style={styles.footerText}>{item.likeCount}</Text></View>
        <View style={styles.footerItem}><Ionicons name="eye" size={14} color="#999" /><Text style={styles.footerText}>{item.viewCount}</Text></View>
      </View>
    </TouchableOpacity>
  );

  // 로딩 중일 때 돌릴 스피너 화면
  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#fff' }}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 10 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>우리 동네 상점</Text>
      </View>

      <View style={styles.categoryWrapper}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          {CATEGORIES.map((cat) => (
            <TouchableOpacity 
              key={cat} 
              style={[styles.categoryPill, selectedCategory === cat && styles.categoryPillActive]}
              onPress={() => setSelectedCategory(cat)} 
            >
              <Text style={[styles.categoryText, selectedCategory === cat && styles.categoryTextActive]}>{cat}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      <View style={styles.listHeader}>
        <Text style={styles.totalText}>총 {shopList.length}개</Text>
        <TouchableOpacity style={styles.sortButton}>
          <Text style={styles.sortText}>최신순</Text>
          <Ionicons name="chevron-down" size={14} color="#666" />
        </TouchableOpacity>
      </View>

      <FlatList
        data={shopList}
        keyExtractor={(item) => item.id}
        renderItem={renderShopCard}
        numColumns={2}
        columnWrapperStyle={styles.rowWrapper}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 30 }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15 },
  headerTitle: { fontSize: 18, color: '#333' },
  categoryWrapper: { paddingLeft: 20, marginBottom: 15 },
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
  footerRow: { flexDirection: 'row', alignItems: 'center' },
  footerItem: { flexDirection: 'row', alignItems: 'center', marginRight: 10 },
  footerText: { fontSize: 11, color: '#999', marginLeft: 4 },
});