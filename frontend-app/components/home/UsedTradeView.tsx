import React, { useState, useEffect, useMemo } from 'react';
import { View, StyleSheet, TouchableOpacity, FlatList, Image, Dimensions, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText'; // 경로에 맞게 수정해주세요

const { width } = Dimensions.get('window');

// 💡 1. 임시 목(Mock) 데이터 구성
const MOCK_USED_PRODUCTS = [
  { id: '1', title: '정승민을 찾습니다', price: 6160, location: '인천 부평구', timeAgo: '1시간전', chatCount: 120, likeCount: 120, status: 'RESERVED', img: 'https://via.placeholder.com/150/F5F5F5/999999?text=Egg', regionId: 1 },
  { id: '2', title: '정승민을 찾습니다', price: 6160, location: '인천 부평구', timeAgo: '1시간전', chatCount: 120, likeCount: 120, status: 'ON_SALE', img: 'https://via.placeholder.com/150/F5F5F5/999999?text=Egg', regionId: 1 },
  { id: '3', title: '포켓몬 카드', price: 6500, location: '인천 부평구', timeAgo: '1시간전', chatCount: 120, likeCount: 120, status: 'SOLD_OUT', img: 'https://via.placeholder.com/150/FFD700/FFFFFF?text=Card', regionId: 1 },
  { id: '4', title: '정승민을 찾습니다', price: 6160, location: '인천 부평구', timeAgo: '1시간전', chatCount: 120, likeCount: 120, status: 'ON_SALE', img: 'https://via.placeholder.com/150/F5F5F5/999999?text=Egg', regionId: 1 },
];

interface UsedTradeViewProps {
  router: any;
  regionId?: number | null; // 부모로부터 동네 ID를 받음
}

export default function UsedTradeView({ router, regionId }: UsedTradeViewProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [hasNoRegion, setHasNoRegion] = useState(false);
  const [products, setProducts] = useState<any[]>([]);
  
  // 필터 상태
  const [includeSold, setIncludeSold] = useState(false);
  const [includeReserved, setIncludeReserved] = useState(false);

  // 💡 2. 데이터 패칭 로직 (무조건 데이터가 보이도록 수정)
  useEffect(() => {
    const fetchTradeData = async () => {
      setIsLoading(true);
      setHasNoRegion(false); // 🚨 강제로 안내 문구를 끕니다!

      try {
        // [나중에 추가할 API 연동 자리]
        // const res = await usedTradeApi.getProducts({ regionId });
        
        // 부모가 regionId를 안 줘도 에러가 나지 않도록 임시로 '1'을 기본값으로 줍니다.
        const currentRegionId = regionId || 1; 
        const fetchedProducts = MOCK_USED_PRODUCTS.filter(p => p.regionId === currentRegionId);
        
        setProducts(fetchedProducts);
      } catch (error) {
        console.error('중고거래 목록 로딩 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchTradeData();
  }, [regionId]);

  // 💡 3. 상태(판매완료, 예약중) 필터링 로직
  const filteredProducts = useMemo(() => {
    return products.filter(p => {
      if (!includeSold && p.status === 'SOLD_OUT') return false;
      if (!includeReserved && p.status === 'RESERVED') return false;
      return true;
    });
  }, [products, includeSold, includeReserved]);

  // 💡 4. 예외 화면 처리 (로딩 중일 때만 처리하고 hasNoRegion은 지웠습니다!)
  if (isLoading) {
    return (
      <View style={[styles.center, { paddingTop: 50 }]}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  if (hasNoRegion) {
    return (
      <View style={[styles.center, { paddingTop: 50 }]}>
        <Text style={styles.emptyText}>먼저 상단에서 동네를 설정해주세요!</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* 1. 필터 및 정렬 옵션 (중복되던 위치/검색창은 깔끔하게 삭제) */}
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
        <TouchableOpacity style={styles.sortButton}>
          <Text style={styles.sortText}>최신순</Text>
        </TouchableOpacity>
      </View>

      {/* 2. 상품 그리드 목록 */}
      <FlatList
        data={filteredProducts}
        keyExtractor={(item) => item.id}
        numColumns={2}
        columnWrapperStyle={styles.rowWrapper}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 100 }}
        renderItem={({ item }) => (
          <TouchableOpacity 
            style={styles.productCard} 
            onPress={() => router.push({ pathname: '/used-trade/[id]', params: { id: item.id } })}
          >
            <View style={styles.imageContainer}>
              <Image source={{ uri: item.img }} style={styles.productImage} />
              
              {/* 예약중 / 거래완료 뱃지 */}
              {item.status === 'RESERVED' && (
                <View style={[styles.statusBadge, { backgroundColor: '#1B854A' }]}>
                  <Text style={styles.statusBadgeText}>예약중</Text>
                </View>
              )}
              {item.status === 'SOLD_OUT' && (
                <View style={[styles.statusBadge, { backgroundColor: '#555' }]}>
                  <Text style={styles.statusBadgeText}>거래완료</Text>
                </View>
              )}

              {/* 하얀색 원형 찜 버튼 */}
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
                  <Text style={styles.metaText}>{item.location}</Text>
                </View>
                <Text style={styles.metaText}>{item.timeAgo}</Text>
              </View>

              <View style={styles.priceRow}>
                <Text style={styles.priceText}>{item.price.toLocaleString()} 원</Text>
                <View style={styles.iconsRow}>
                  <Ionicons name="chatbubble-ellipses-outline" size={12} color="#999" />
                  <Text style={styles.iconText}>{item.chatCount}</Text>
                  <Ionicons name="heart" size={12} color="#999" style={{ marginLeft: 6 }} />
                  <Text style={styles.iconText}>{item.likeCount}</Text>
                </View>
              </View>
            </View>
          </TouchableOpacity>
        )}
      />

      {/* 3. 글쓰기 플로팅 액션 버튼 */}
      <TouchableOpacity 
        style={styles.fab} 
        onPress={() => router.push('/used-trade/write')}
      >
        <Ionicons name="add" size={32} color="#fff" />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { alignItems: 'center', justifyContent: 'center' },
  emptyText: { color: '#888', fontSize: 14 },
  
  // 필터 및 정렬
  filterHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingTop: 10, paddingBottom: 15 },
  filterPills: { flexDirection: 'row' },
  filterPill: { backgroundColor: '#F5F5F5', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, marginRight: 8 },
  filterPillActive: { backgroundColor: '#333' },
  filterPillText: { fontSize: 12, color: '#666' },
  filterPillTextActive: { color: '#FFF' },
  sortButton: { padding: 4 },
  sortText: { fontSize: 13, color: '#666' },
  
  // 리스트 그리드
  rowWrapper: { justifyContent: 'space-between', paddingHorizontal: 15 },
  productCard: { width: (width - 45) / 2, marginBottom: 20 },
  
  // 이미지 및 오버레이
  imageContainer: { width: '100%', height: (width - 45) / 2, borderRadius: 8, overflow: 'hidden', backgroundColor: '#F9F9F9', position: 'relative' },
  productImage: { width: '100%', height: '100%' },
  statusBadge: { position: 'absolute', top: 0, left: 0, paddingHorizontal: 8, paddingVertical: 4, borderBottomRightRadius: 8 },
  statusBadgeText: { color: '#FFF', fontSize: 11, fontWeight: 'bold' },
  heartButton: { position: 'absolute', bottom: 8, right: 8, backgroundColor: '#FFF', width: 30, height: 30, borderRadius: 15, justifyContent: 'center', alignItems: 'center', elevation: 2, shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 3, shadowOffset: { width: 0, height: 1 } },
  
  // 상품 하단 정보
  cardInfo: { paddingTop: 10 },
  titleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  productTitle: { fontSize: 15, fontWeight: 'bold', color: '#333', flex: 1, paddingRight: 10 },
  locationRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  metaText: { fontSize: 11, color: '#999', marginLeft: 2 },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  priceText: { fontSize: 15, fontWeight: 'bold', color: '#333' },
  iconsRow: { flexDirection: 'row', alignItems: 'center' },
  iconText: { fontSize: 11, color: '#999', marginLeft: 3 },
  
  // 플로팅 버튼
  fab: { position: 'absolute', bottom: 20, right: 20, width: 60, height: 60, borderRadius: 30, backgroundColor: '#00A859', justifyContent: 'center', alignItems: 'center', elevation: 4, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 3.84 },
});