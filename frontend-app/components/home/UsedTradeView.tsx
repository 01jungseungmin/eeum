import React, { useState, useMemo, useCallback } from 'react';
import { View, StyleSheet, TouchableOpacity, FlatList, Image, Dimensions, ActivityIndicator, Modal, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';
import { usedApi, UsedProductStatus } from '../../api/used';
import { USED_CATEGORIES, USED_SORT_OPTIONS, UsedSortKey } from '../../constants/usedCategories';
import { useFocusEffect } from 'expo-router';

const { width } = Dimensions.get('window');

interface UsedTradeViewProps {
  router: any;
  regionId?: number | null;
}

export default function UsedTradeView({ router, regionId }: UsedTradeViewProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [products, setProducts] = useState<any[]>([]);

  // 필터 상태
  const [includeSold, setIncludeSold] = useState(false);
  const [includeReserved, setIncludeReserved] = useState(false);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [sortKey, setSortKey] = useState<UsedSortKey>('LATEST');
  const [isSortOpen, setIsSortOpen] = useState(false);

  const activeSort = USED_SORT_OPTIONS.find(o => o.key === sortKey) ?? USED_SORT_OPTIONS[0];

  // 상태 필터는 서버로 넘긴다. 화면에서 걸러내면 한 페이지에 담기는 건수가 들쭉날쭉해진다.
  const statusFilter = useMemo<UsedProductStatus[]>(() => {
    const list: UsedProductStatus[] = ['SELLING'];
    if (includeReserved) list.push('RESERVED');
    if (includeSold) list.push('SOLD');
    return list;
  }, [includeReserved, includeSold]);

  useFocusEffect(
    useCallback(() => {
      let isActive = true;

      const fetchTradeData = async () => {
        if (!regionId) {
          setProducts([]);
          return;
        }

        setIsLoading(true);

        try {
          const res = await usedApi.getUsedProducts({
            regionId,
            categoryId,
            status: statusFilter,
            sort: activeSort.sort,
          });

          // 응답에서 데이터 추출
          const fetchedProducts = res.data?.data?.content ?? [];
          if (isActive) setProducts(fetchedProducts);
        } catch (error) {
          console.error('중고거래 목록 로딩 실패:', error);
        } finally {
          if (isActive) setIsLoading(false);
        }
      };

      fetchTradeData();

      return () => { isActive = false; };
    }, [regionId, categoryId, statusFilter, activeSort.sort]) // 필터나 정렬이 바뀌어도 다시 불러옴
  );

  const filteredProducts = products;

  return (
    <View style={styles.container}>
      {/* 카테고리 필터 */}
      <View style={styles.categoryWrapper}>
        <FlatList
          data={USED_CATEGORIES}
          horizontal
          showsHorizontalScrollIndicator={false}
          keyExtractor={(item) => String(item.id ?? 'all')}
          ListHeaderComponent={<View style={{ width: 15 }} />}
          ListFooterComponent={<View style={{ width: 15 }} />}
          renderItem={({ item }) => {
            const active = categoryId === item.id;
            return (
              <TouchableOpacity
                style={[styles.categoryPill, active && styles.categoryPillActive]}
                onPress={() => setCategoryId(item.id)}
              >
                <Text style={[styles.categoryPillText, active && styles.categoryPillTextActive]}>
                  {item.name}
                </Text>
              </TouchableOpacity>
            );
          }}
        />
      </View>

      <View style={styles.filterHeader}>
        <View style={styles.filterPills}>
          <TouchableOpacity
            style={[styles.filterPill, includeSold && styles.filterPillActive]}
            onPress={() => setIncludeSold(!includeSold)}
          >
            <Text style={[styles.filterPillText, includeSold && styles.filterPillTextActive]}>판매 완료 포함</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.filterPill, includeReserved && styles.filterPillActive]}
            onPress={() => setIncludeReserved(!includeReserved)}
          >
            <Text style={[styles.filterPillText, includeReserved && styles.filterPillTextActive]}>예약중 포함</Text>
          </TouchableOpacity>
        </View>
        <TouchableOpacity style={styles.sortButton} onPress={() => setIsSortOpen(true)}>
          <Text style={styles.sortText}>{activeSort.label}</Text>
          <Ionicons name="chevron-down" size={14} color="#666" style={{ marginLeft: 2 }} />
        </TouchableOpacity>
      </View>

      {/* 정렬 선택 */}
      <Modal
        visible={isSortOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setIsSortOpen(false)}
      >
        <Pressable style={styles.sortBackdrop} onPress={() => setIsSortOpen(false)}>
          <Pressable style={styles.sortSheet}>
            {USED_SORT_OPTIONS.map(option => {
              const active = option.key === sortKey;
              return (
                <TouchableOpacity
                  key={option.key}
                  style={styles.sortOption}
                  onPress={() => {
                    setSortKey(option.key);
                    setIsSortOpen(false);
                  }}
                >
                  <Text style={[styles.sortOptionText, active && styles.sortOptionTextActive]}>
                    {option.label}
                  </Text>
                  {active && <Ionicons name="checkmark" size={18} color="#00A859" />}
                </TouchableOpacity>
              );
            })}
          </Pressable>
        </Pressable>
      </Modal>

      {isLoading ? (
        <View style={[styles.center, { flex: 1 }]}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : filteredProducts.length === 0 ? (
        <View style={[styles.center, { flex: 1 }]}>
          <Ionicons name="cube-outline" size={48} color="#DDD" style={{ marginBottom: 12 }} />
          <Text style={styles.emptyText}>
            {regionId ? '해당 조건에 맞는 물품이 없어요.' : '동네를 먼저 설정해 주세요!'}
          </Text>
        </View>
      ) : (
      <FlatList
        data={filteredProducts}
        keyExtractor={(item) => item.usedProductId?.toString() || item.id?.toString()}
        numColumns={2}
        columnWrapperStyle={styles.rowWrapper}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 100 }}
        renderItem={({ item }) => (
          <TouchableOpacity 
            style={styles.productCard} 
            onPress={() => router.push({ pathname: '/used-trade/[id]', params: { id: item.usedProductId || item.id } })}
          >
            <View style={styles.imageContainer}>
              {/* 대표 사진 렌더링 (Swagger 명세 필드명에 맞게 조정 필요) */}
              <Image source={{ uri: item.thumbnailUrl || item.img || 'https://via.placeholder.com/150' }} style={styles.productImage} />
              
              {item.status === 'RESERVED' && (
                <View style={[styles.statusBadge, { backgroundColor: '#1B854A' }]}>
                  <Text style={styles.statusBadgeText}>예약중</Text>
                </View>
              )}
              {item.status === 'SOLD' && (
                <View style={[styles.statusBadge, { backgroundColor: '#555' }]}>
                  <Text style={styles.statusBadgeText}>거래완료</Text>
                </View>
              )}

              <TouchableOpacity style={styles.heartButton}>
                <Ionicons name="heart" size={18} color="#CCC" />
              </TouchableOpacity>
            </View>

            <View style={styles.cardInfo}>
              <View style={styles.titleRow}>
                <Text style={styles.productTitle} numberOfLines={1}>{item.title}</Text>
                <Ionicons name="ellipsis-vertical" size={16} color="#999" />
              </View>
              
              <View style={styles.locationRow}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Ionicons name="location-outline" size={12} color="#999" />

                  <Text style={styles.metaText}>{item.tradeLocation || item.regionName}</Text>
                </View>

                <Text style={styles.metaText}>{item.createdAt ? item.createdAt.substring(0,10) : '방금 전'}</Text>
              </View>

              <View style={styles.priceRow}>
                <Text style={styles.priceText}>{item.price?.toLocaleString()} 원</Text>
                <View style={styles.iconsRow}>

                  <Ionicons name="chatbubble-ellipses-outline" size={12} color="#999" />
                  <Text style={styles.iconText}>{item.viewCount || 0}</Text>
                  
                  <Ionicons name="heart" size={12} color="#999" style={{ marginLeft: 6 }} />
                  <Text style={styles.iconText}>{item.favoriteCount || 0}</Text>
                </View>
              </View>
            </View>
          </TouchableOpacity>
        )}
      />
      )}

      <TouchableOpacity 
        style={styles.fab} 
        onPress={() => router.push({ 
          pathname: '/used-trade/write', 
          params: { regionId: regionId } 
        })}
      >
        <Ionicons name="add" size={32} color="#fff" />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { alignItems: 'center', justifyContent: 'center' },
  
  emptyText: { fontSize: 15, color: '#888' },

  categoryWrapper: { paddingTop: 10 },
  categoryPill: { backgroundColor: '#F5F5F5', paddingHorizontal: 14, paddingVertical: 7, borderRadius: 16, marginRight: 8 },
  categoryPillActive: { backgroundColor: '#00A859' },
  categoryPillText: { fontSize: 13, color: '#666' },
  categoryPillTextActive: { color: '#FFF', fontWeight: 'bold' },

  sortBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'flex-end' },
  sortSheet: { backgroundColor: '#FFF', borderTopLeftRadius: 16, borderTopRightRadius: 16, paddingVertical: 8, paddingBottom: 28 },
  sortOption: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 24, paddingVertical: 16 },
  sortOptionText: { fontSize: 15, color: '#333' },
  sortOptionTextActive: { color: '#00A859', fontWeight: 'bold' },

  filterHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingTop: 10, paddingBottom: 15 },
  filterPills: { flexDirection: 'row' },
  filterPill: { backgroundColor: '#F5F5F5', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, marginRight: 8 },
  filterPillActive: { backgroundColor: '#333' },
  filterPillText: { fontSize: 12, color: '#666' },
  filterPillTextActive: { color: '#FFF' },
  sortButton: { padding: 4 },
  sortText: { fontSize: 13, color: '#666' },
  
  rowWrapper: { justifyContent: 'space-between', paddingHorizontal: 15 },
  productCard: { width: (width - 45) / 2, marginBottom: 20 },
  
  imageContainer: { width: '100%', height: (width - 45) / 2, borderRadius: 8, overflow: 'hidden', backgroundColor: '#F9F9F9', position: 'relative' },
  productImage: { width: '100%', height: '100%' },
  statusBadge: { position: 'absolute', top: 0, left: 0, paddingHorizontal: 8, paddingVertical: 4, borderBottomRightRadius: 8 },
  statusBadgeText: { color: '#FFF', fontSize: 11, fontWeight: 'bold' },
  heartButton: { position: 'absolute', bottom: 8, right: 8, backgroundColor: '#FFF', width: 30, height: 30, borderRadius: 15, justifyContent: 'center', alignItems: 'center', elevation: 2, shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 3, shadowOffset: { width: 0, height: 1 } },
  
  cardInfo: { paddingTop: 10 },
  titleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  productTitle: { fontSize: 15, fontWeight: 'bold', color: '#333', flex: 1, paddingRight: 10 },
  locationRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  metaText: { fontSize: 11, color: '#999', marginLeft: 2 },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  priceText: { fontSize: 15, fontWeight: 'bold', color: '#333' },
  iconsRow: { flexDirection: 'row', alignItems: 'center' },
  iconText: { fontSize: 11, color: '#999', marginLeft: 3 },
  
  fab: { position: 'absolute', bottom: 20, right: 20, width: 60, height: 60, borderRadius: 30, backgroundColor: '#00A859', justifyContent: 'center', alignItems: 'center', elevation: 4, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 3.84 },
});