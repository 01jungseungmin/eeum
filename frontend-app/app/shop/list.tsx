import React, { useState, useEffect, useRef } from 'react';
import { View, StyleSheet, FlatList, Image, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router'; 
import { Text } from '../../components/CustomText';
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';
import { shopApi } from '../../api/shop';
import { regionApi } from '../../api/region'; // 찜 브랜치 로직 유지

export default function ShopListScreen() {
  const router = useRouter();
  const { regionId } = useLocalSearchParams(); 

  const [selectedCategoryId, setSelectedCategoryId] = useState<number>(0);
  const [shopList, setShopList] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const categoryListRef = useRef<FlatList<any>>(null);
  
  const handleCategoryPress = (id: number, index: number) => {
    setSelectedCategoryId(id);
    setTimeout(() => {
      try {
        categoryListRef.current?.scrollToIndex({ index, animated: true, viewPosition: 0.5 });
      } catch (e) { console.log(e); }
    }, 50); 
  };

  useEffect(() => {
    const fetchShops = async () => {
      setIsLoading(true);
      try {
        // favorite 브랜치와 동일한 데이터 조회 로직 사용
        let targetRegionId = regionId ? Number(regionId) : null;
        
        if (!targetRegionId) {
          const res = await regionApi.getMyRegions();
          const regions = res.data || [];
          const primary = regions.find((r: any) => r.isPrimary === true);
          if (primary) targetRegionId = primary.regionId;
        }

        const categoryParam = selectedCategoryId === 0 ? undefined : selectedCategoryId;
        const params: any = { categoryId: categoryParam, size: 20 };
        if (targetRegionId) params.regionId = targetRegionId;

        const res = await shopApi.getShops(params);
        // 서버 응답 구조(res.data.content) 그대로 사용
        setShopList(res.data?.content || []);
      } catch (e) {
        console.error('상점 목록 로딩 실패:', e);
      } finally {
        setIsLoading(false);
      }
    };
    fetchShops();
  }, [selectedCategoryId, regionId]); 

  const getCategoryName = (id: number) => SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';

  const renderShopCard = ({ item }: any) => {
    const thumbnailUrl = item.thumbnailUrl || 'https://via.placeholder.com/300/E8F5E9/00A859?text=Store';

    return (
      <TouchableOpacity style={styles.cardContainer} onPress={() => router.push(`/shop/${item.storeId}`)}>
        <Image source={{ uri: thumbnailUrl }} style={styles.cardImage} />
        <View style={styles.cardTitleRow}>
          <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
        </View>
        <View style={styles.ratingRow}>
          <Ionicons name="star" size={14} color="#FFD700" />
          <Text fontWeight="bold" style={styles.ratingText}>{item.rating?.toFixed(1) || '0.0'}</Text>
          <Text style={styles.reviewText}>({item.reviewCount || 0})</Text>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 10 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>우리 동네 상점</Text>
      </View>

      <FlatList
        data={shopList}
        keyExtractor={(item) => item.storeId.toString()}
        renderItem={renderShopCard}
        numColumns={2}
        columnWrapperStyle={styles.rowWrapper}
        style={{ flex: 1 }}
        ListHeaderComponent={
          <View style={styles.categoryWrapper}>
            <FlatList
              ref={categoryListRef}
              data={SHOP_CATEGORIES || []}
              horizontal
              showsHorizontalScrollIndicator={false}
              renderItem={({ item, index }) => (
                <TouchableOpacity 
                  style={[styles.categoryPill, selectedCategoryId === item.id && styles.categoryPillActive]}
                  onPress={() => handleCategoryPress(item.id, index)} 
                >
                  <Text style={[styles.categoryText, selectedCategoryId === item.id && styles.categoryTextActive]}>
                    {item.name}
                  </Text>
                </TouchableOpacity>
              )}
            />
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15 },
  headerTitle: { fontSize: 18, color: '#333' },
  categoryWrapper: { marginBottom: 15, paddingHorizontal: 20 },
  categoryPill: { backgroundColor: '#F5F5F5', paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, marginRight: 8 },
  categoryPillActive: { backgroundColor: '#00A859' },
  categoryText: { color: '#666', fontSize: 14 },
  categoryTextActive: { color: '#fff', fontWeight: 'bold' },
  rowWrapper: { justifyContent: 'space-between', paddingHorizontal: 20 },
  cardContainer: { width: '48%', marginBottom: 25 },
  cardImage: { width: '100%', aspectRatio: 1, borderRadius: 12, marginBottom: 10 },
  cardTitleRow: { marginBottom: 4 },
  shopName: { fontSize: 16, color: '#333' },
  ratingRow: { flexDirection: 'row', alignItems: 'center' },
  ratingText: { fontSize: 13, color: '#333', marginLeft: 4 },
  reviewText: { fontSize: 12, color: '#888', marginLeft: 4 }
});