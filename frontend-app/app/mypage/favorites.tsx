import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet, View, FlatList, Image,
  TouchableOpacity, ActivityIndicator, Alert
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';

import {
  favoriteApi,
  FavoriteStore,
  FavoriteUsedProduct,
  UsedProductPriceType,
  UsedProductStatus,
} from '../../api/favorite';
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';

type TabKey = 'STORE' | 'USED_PRODUCT';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'STORE', label: '상점' },
  { key: 'USED_PRODUCT', label: '중고거래' },
];

const PAGE_SIZE = 20;

const PRICE_TYPE_LABEL: Record<UsedProductPriceType, string> = {
  FIXED: '',
  NEGOTIABLE: '가격 협의',
  FREE: '무료 나눔',
};

const STATUS_LABEL: Record<UsedProductStatus, string> = {
  SELLING: '판매중',
  RESERVED: '예약중',
  SOLD: '판매완료',
};

export default function FavoritesScreen() {
  const router = useRouter();

  const [activeTab, setActiveTab] = useState<TabKey>('STORE');

  const [stores, setStores] = useState<FavoriteStore[]>([]);
  const [usedProducts, setUsedProducts] = useState<FavoriteUsedProduct[]>([]);

  // 탭별로 페이지 커서를 따로 들고 있어야 탭을 오갈 때 처음부터 다시 받지 않는다.
  const [cursors, setCursors] = useState<Record<TabKey, { page: number; hasNext: boolean }>>({
    STORE: { page: 0, hasNext: true },
    USED_PRODUCT: { page: 0, hasNext: true },
  });
  const [loaded, setLoaded] = useState<Record<TabKey, boolean>>({
    STORE: false,
    USED_PRODUCT: false,
  });

  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  const fetchPage = useCallback(async (tab: TabKey, page: number) => {
    return tab === 'STORE'
      ? favoriteApi.getMyFavoriteStores({ page, size: PAGE_SIZE })
      : favoriteApi.getMyFavoriteUsedProducts({ page, size: PAGE_SIZE });
  }, []);

  const loadFirstPage = useCallback(async (tab: TabKey) => {
    try {
      setIsLoading(true);
      const result = await fetchPage(tab, 0);

      if (tab === 'STORE') {
        setStores(result.content as FavoriteStore[]);
      } else {
        setUsedProducts(result.content as FavoriteUsedProduct[]);
      }

      setCursors(prev => ({ ...prev, [tab]: { page: 0, hasNext: result.hasNext } }));
      setLoaded(prev => ({ ...prev, [tab]: true }));
    } catch (error) {
      console.error('찜 목록 API 에러:', error);
      Alert.alert('오류', '찜 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  }, [fetchPage]);

  const loadNextPage = useCallback(async () => {
    const cursor = cursors[activeTab];
    if (!cursor.hasNext || isLoading || isLoadingMore) return;

    try {
      setIsLoadingMore(true);
      const nextPage = cursor.page + 1;
      const result = await fetchPage(activeTab, nextPage);

      if (activeTab === 'STORE') {
        setStores(prev => [...prev, ...(result.content as FavoriteStore[])]);
      } else {
        setUsedProducts(prev => [...prev, ...(result.content as FavoriteUsedProduct[])]);
      }

      setCursors(prev => ({
        ...prev,
        [activeTab]: { page: nextPage, hasNext: result.hasNext },
      }));
    } catch (error) {
      console.error('찜 목록 추가 로드 실패:', error);
    } finally {
      setIsLoadingMore(false);
    }
  }, [activeTab, cursors, fetchPage, isLoading, isLoadingMore]);

  useEffect(() => {
    // 이미 받아온 탭은 다시 요청하지 않는다.
    if (loaded[activeTab]) {
      setIsLoading(false);
      return;
    }
    loadFirstPage(activeTab);
  }, [activeTab, loaded, loadFirstPage]);

  /**
   * 찜 해제.
   *
   * 목록은 favoriteId를 이미 알고 있으니 토글 대신 DELETE를 쓴다.
   * 토글은 다른 기기에서 먼저 해제된 경우 다시 등록해버린다.
   */
  const handleRemoveFavorite = async (favoriteId: number) => {
    try {
      await favoriteApi.deleteFavorite(favoriteId);

      if (activeTab === 'STORE') {
        setStores(prev => prev.filter(item => item.favoriteId !== favoriteId));
      } else {
        setUsedProducts(prev => prev.filter(item => item.favoriteId !== favoriteId));
      }
    } catch (error) {
      Alert.alert('알림', '찜 해제에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  const formatPrice = (item: FavoriteUsedProduct) => {
    if (item.priceType !== 'FIXED') return PRICE_TYPE_LABEL[item.priceType];
    return item.price != null ? `${Number(item.price).toLocaleString()}원` : '';
  };

  const renderStoreItem = ({ item }: { item: FavoriteStore }) => (
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
          <Text style={styles.categoryText}>{getCategoryName((item as any).categoryId)}</Text>
          <TouchableOpacity
            onPress={() => handleRemoveFavorite(item.favoriteId)}
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

  // 중고 게시글 상세 화면이 앱에 아직 없어 카드 자체는 이동하지 않는다.
  // 상세 라우트가 생기면 View를 TouchableOpacity로 되돌리고 push만 붙이면 된다.
  const renderUsedProductItem = ({ item }: { item: FavoriteUsedProduct }) => (
    <View style={styles.cardContainer}>
      <Image
        source={{ uri: item.thumbnailUrl || 'https://via.placeholder.com/150' }}
        style={styles.cardImage}
      />

      <View style={styles.cardInfo}>
        <View style={styles.cardHeader}>
          <View style={[styles.statusBadge, item.status === 'SOLD' && styles.statusBadgeSold]}>
            <Text style={[styles.statusText, item.status === 'SOLD' && styles.statusTextSold]}>
              {STATUS_LABEL[item.status]}
            </Text>
          </View>
          <TouchableOpacity
            onPress={() => handleRemoveFavorite(item.favoriteId)}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          >
            <Ionicons name="heart" size={24} color="#FF5252" />
          </TouchableOpacity>
        </View>

        <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.title}</Text>
        <Text fontWeight="bold" style={styles.priceText}>{formatPrice(item)}</Text>
        <Text style={styles.addressText} numberOfLines={1}>{item.regionName || '동네 정보 없음'}</Text>
      </View>
    </View>
  );

  const isStoreTab = activeTab === 'STORE';
  const listData = isStoreTab ? stores : usedProducts;

  const renderEmpty = () => (
    <View style={styles.centerContainer}>
      <Ionicons name="heart-dislike-outline" size={60} color="#DDD" style={{ marginBottom: 15 }} />
      <Text style={styles.emptyText}>
        {isStoreTab ? '아직 찜한 상점이 없어요.' : '아직 찜한 중고 게시글이 없어요.'}
      </Text>
      <Text style={styles.emptySubText}>
        {isStoreTab
          ? '자주 가는 상점을 단골로 등록해 보세요!'
          : '관심 있는 중고 게시글에 하트를 눌러 보세요!'}
      </Text>
    </View>
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

      <View style={styles.tabBar}>
        {TABS.map(tab => {
          const active = tab.key === activeTab;
          return (
            <TouchableOpacity
              key={tab.key}
              style={[styles.tabItem, active && styles.tabItemActive]}
              onPress={() => setActiveTab(tab.key)}
            >
              <Text
                fontWeight={active ? 'bold' : 'normal'}
                style={[styles.tabText, active && styles.tabTextActive]}
              >
                {tab.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {isLoading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : listData.length === 0 ? (
        renderEmpty()
      ) : (
        <FlatList
          data={listData as any[]}
          keyExtractor={(item) => String(item.favoriteId)}
          renderItem={(isStoreTab ? renderStoreItem : renderUsedProductItem) as any}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
          onEndReached={loadNextPage}
          onEndReachedThreshold={0.4}
          ListFooterComponent={
            isLoadingMore ? (
              <ActivityIndicator style={{ marginVertical: 20 }} color="#00A859" />
            ) : null
          }
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

  tabBar: {
    flexDirection: 'row', backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#F0F0F0'
  },
  tabItem: {
    flex: 1, alignItems: 'center', paddingVertical: 14,
    borderBottomWidth: 2, borderBottomColor: 'transparent'
  },
  tabItemActive: { borderBottomColor: '#00A859' },
  tabText: { fontSize: 15, color: '#999' },
  tabTextActive: { color: '#00A859' },

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
  addressText: { fontSize: 12, color: '#888' },

  priceText: { fontSize: 15, color: '#333', marginBottom: 4 },
  statusBadge: {
    backgroundColor: '#E8F5E9', paddingHorizontal: 8, paddingVertical: 2, borderRadius: 4
  },
  statusBadgeSold: { backgroundColor: '#EEE' },
  statusText: { fontSize: 11, color: '#00A859', fontWeight: 'bold' },
  statusTextSold: { color: '#888' },
});
