import React, { useState, useEffect } from 'react';
import { 
  StyleSheet, View, FlatList, Image, 
  TouchableOpacity, ActivityIndicator, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';

import { shopApi } from '@/api/shop';
import { favoriteApi } from '../../api/favorite'; 
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';

export default function FavoritesScreen() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [favorites, setFavorites] = useState<any[]>([]);

  useEffect(() => {
    fetchFavorites();
  }, []);

  const fetchFavorites = async () => {
    try {
      setIsLoading(true);

      // 1. 서버에 찜 목록 요청
      const res = await favoriteApi.getMyFavorites('STORE');
      
      // 2. 응답 데이터에서 배열만 쏙 빼오기
      const favoritesList = res.data?.data?.content || [];
      
      // 3. 복잡한 가공(Promise.all 등) 없이, 바로 state에 저장! ✨
      setFavorites(favoritesList);
      
    } catch (error) {
      console.error('찜 목록 API 에러:', error);
      Alert.alert('오류', '찜 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };
  
  const handleRemoveFavorite = async (storeId: number) => {
    try {
      await favoriteApi.toggleFavorite('STORE', storeId);
      setFavorites(prev => prev.filter(shop => shop.storeId !== storeId));
    } catch (error) {
      Alert.alert('알림', '찜 해제에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  const renderFavoriteItem = ({ item }: { item: any }) => (
    <TouchableOpacity 
      style={styles.cardContainer} 
      onPress={() => router.push(`/shop/${item.storeId}` as any)}
    >
      <Image 
        source={{ uri: item.thumbnailUrl || 'https://via.placeholder.com/150' }} 
        style={styles.cardImage} 
      />
      
      <View style={styles.cardInfo}>
        <View style={styles.cardHeader}>
          <Text style={styles.categoryText}>{getCategoryName(item.categoryId)}</Text>
          <TouchableOpacity 
            onPress={() => handleRemoveFavorite(item.storeId)}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          >
            <Ionicons name="heart" size={24} color="#FF5252" />
          </TouchableOpacity>
        </View>

        <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
        
        <View style={styles.ratingRow}>
          <Ionicons name="star" size={14} color="#FFD700" />
          <Text fontWeight="bold" style={styles.ratingText}>{item.rating?.toFixed(1) || '0.0'}</Text>
          <Text style={styles.reviewText}>({item.reviewCount || 0})</Text>
        </View>

        <Text style={styles.addressText} numberOfLines={1}>{item.address}</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>찜 목록</Text>
        <View style={{ width: 24 }} />
      </View>

      {isLoading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : favorites.length === 0 ? (
        <View style={styles.centerContainer}>
          <Ionicons name="heart-dislike-outline" size={60} color="#DDD" style={{ marginBottom: 15 }} />
          <Text style={styles.emptyText}>아직 찜한 상점이 없어요.</Text>
          <Text style={styles.emptySubText}>자주 가는 상점을 단골로 등록해 보세요!</Text>
        </View>
      ) : (
        <FlatList
          data={favorites}
          keyExtractor={(item) => item.storeId.toString()}
          renderItem={renderFavoriteItem}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    paddingHorizontal: 20, paddingVertical: 15, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#F0F0F0'
  },
  backButton: { padding: 4, marginLeft: -4 },
  headerTitle: { fontSize: 18, color: '#333' },
  
  centerContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F8F9FA' },
  emptyText: { fontSize: 16, color: '#666', fontWeight: 'bold', marginBottom: 8 },
  emptySubText: { fontSize: 14, color: '#999' },
  
  listContainer: { padding: 20 },
  cardContainer: { 
    flexDirection: 'row', backgroundColor: '#fff', borderRadius: 12, 
    padding: 12, marginBottom: 15,
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 5, elevation: 3
  },
  cardImage: { width: 80, height: 80, borderRadius: 8, marginRight: 15 },
  cardInfo: { flex: 1, justifyContent: 'center' },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  categoryText: { fontSize: 12, color: '#00A859', fontWeight: 'bold' },
  shopName: { fontSize: 16, color: '#333', marginBottom: 6 },
  ratingRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  ratingText: { fontSize: 13, color: '#333', marginLeft: 4, marginRight: 4 },
  reviewText: { fontSize: 12, color: '#888' },
  addressText: { fontSize: 12, color: '#888' }
});