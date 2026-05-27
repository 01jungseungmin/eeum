import React, { useState, useEffect } from 'react';
import { 
  View, 
  StyleSheet, 
  FlatList, 
  Image, 
  TouchableOpacity, 
  ScrollView,
  ActivityIndicator
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';

// 💡 작성하신 더미 데이터 불러오기
import { SHOP_CATEGORIES, DUMMY_SHOPS } from '../../constants/shopDummyData';
// import { shopApi } from '../../api/shop'; 

export default function ShopListScreen() {
  const router = useRouter();
  // 상태를 카테고리 '이름'이 아니라 'id(숫자)'로 관리합니다. (0은 전체)
  const [selectedCategoryId, setSelectedCategoryId] = useState<number>(0);
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false); 

  // [더미 데이터용 필터 로직] 
  useEffect(() => {
    if (selectedCategoryId === 0) {
      setShopList(DUMMY_SHOPS);
    } else {
      const filtered = DUMMY_SHOPS.filter(shop => shop.categoryId === selectedCategoryId);
      setShopList(filtered);
    }
  }, [selectedCategoryId]);

  /* 🚧 [백엔드 API 연동 시 주석 해제]
  useEffect(() => {
    const fetchShopList = async () => {
      setIsLoading(true);
      try {
        // 카테고리가 전체(0)일 때는 null 전송, 아닐 때는 id 전송
        const categoryParam = selectedCategoryId === 0 ? null : selectedCategoryId;
        const res = await shopApi.getShops(categoryParam);
        setShopList(res.data);
      } catch (e) {
        console.error('상점 목록 API 로딩 실패:', e);
      } finally {
        setIsLoading(false);
      }
    };
    fetchShopList();
  }, [selectedCategoryId]);
  */

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  const renderShopCard = ({ item }: any) => {
    // 썸네일은 첫 번째 상품의 이미지를 가져오거나, 없으면 플레이스홀더 사용
    const thumbnailUrl = item.products?.[0]?.imageUrl || 'https://via.placeholder.com/300/E8F5E9/00A859?text=Store';

    return (
      <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.id}`)}>
        <Image source={{ uri: thumbnailUrl }} style={styles.cardImage} />
        <View style={styles.cardTitleRow}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
          <Ionicons name="heart-outline" size={20} color="#999" />
        </View>
        <View style={styles.ratingRow}>
          <Ionicons name="star" size={14} color="#FFD700" />
          <Text fontWeight="bold" style={styles.ratingText}>{item.rating}</Text>
          <Text style={styles.reviewText}>({item.reviewCount})</Text>
        </View>
        <Text style={styles.locationText}>{getCategoryName(item.categoryId)}</Text>
        <View style={styles.footerRow}>
          <View style={styles.footerItem}><Ionicons name="heart" size={12} color="#999" /><Text style={styles.footerText}>{item.favoriteCount}</Text></View>
        </View>
      </TouchableOpacity>
    );
  };

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
          {(SHOP_CATEGORIES || []).map((cat) => (
            <TouchableOpacity 
              key={cat.id} 
              style={[styles.categoryPill, selectedCategoryId === cat.id && styles.categoryPillActive]}
              onPress={() => setSelectedCategoryId(cat.id)} 
            >
              <Text style={[styles.categoryText, selectedCategoryId === cat.id && styles.categoryTextActive]}>{cat.name}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      <View style={styles.listHeader}>
        <Text style={styles.totalText}>총 {(shopList || []).length}개</Text>
        <TouchableOpacity style={styles.sortButton}>
          <Text style={styles.sortText}>최신순</Text>
          <Ionicons name="chevron-down" size={14} color="#666" />
        </TouchableOpacity>
      </View>

      <FlatList
        data={shopList}
        keyExtractor={(item) => item.id.toString()}
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