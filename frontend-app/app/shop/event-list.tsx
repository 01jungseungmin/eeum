import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, ActivityIndicator } from 'react-native';
import { Text } from '../../components/CustomText'; // 경로가 다르다면 수정해주세요!
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { shopApi } from '../../api/shop';

export default function EventListScreen() {
  const router = useRouter();
  const { regionId } = useLocalSearchParams();
  
  const [eventProducts, setEventProducts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchEventProducts = async () => {
      try {
        setIsLoading(true);
        // 1. 해당 지역의 상점 불러오기
        const shopRes = await shopApi.getShops({ size: 100, regionId: Number(regionId) }); 
        const fetchedShops = shopRes.data?.content || shopRes.data || [];

        if (fetchedShops.length > 0) {
          // 2. 상점별 이벤트 조회
          const eventPromises = fetchedShops.map((shop: any) => 
            shopApi.getEventProducts(shop.storeId).catch(() => null)
          );
          
          const eventResponses = await Promise.all(eventPromises);
          let allEvents: any[] = [];
          
          eventResponses.forEach((res: any, index: number) => {
            if (res && res.data && res.data.length > 0) {
              const productsWithStoreInfo = res.data.map((product: any) => ({
                ...product,
                storeId: fetchedShops[index].storeId,
                storeName: fetchedShops[index].name
              }));
              allEvents = [...allEvents, ...productsWithStoreInfo];
            }
          });

          const activeEvents = allEvents.filter(p => p.ongoing && p.remainingStock > 0);
          setEventProducts(activeEvents);
        }
      } catch (error) {
        console.error('이벤트 상품 목록 로딩 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    if (regionId) fetchEventProducts();
  }, [regionId]);

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 영역 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ padding: 5 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>이벤트 상품 모아보기</Text>
        <View style={{ width: 34 }} />
      </View>

      {/* 리스트 영역 */}
      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : eventProducts.length === 0 ? (
        <View style={styles.center}>
          <Ionicons name="sad-outline" size={48} color="#CCC" style={{ marginBottom: 10 }} />
          <Text style={styles.emptyText}>현재 진행 중인 이벤트 상품이 없어요.</Text>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
          <View style={styles.gridContainer}>
            {eventProducts.map((product, index) => (
              <TouchableOpacity
                key={index}
                style={styles.card}
                onPress={() => router.push(`/shop/${product.storeId}` as any)}
              >
                <View style={styles.imageContainer}>
                  <Image source={{ uri: product.thumbnailUrl || 'https://via.placeholder.com/200' }} style={styles.image} />
                  <View style={styles.stockBadge}>
                    <Text style={styles.stockText}>잔여 {product.remainingStock}개</Text>
                  </View>
                </View>
                
                <Text style={styles.storeName} numberOfLines={1}>{product.storeName}</Text>
                <Text style={styles.productName} numberOfLines={2}>{product.productName}</Text>
                
                <View style={styles.priceContainer}>
                  <Text style={styles.originalPrice}>{product.originalPrice.toLocaleString()}원</Text>
                  <Text style={styles.eventPrice}>{product.eventPrice.toLocaleString()}원</Text>
                </View>
              </TouchableOpacity>
            ))}
          </View>
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#eee' },
  headerTitle: { fontSize: 16, color: '#333' },
  scrollContent: { padding: 15 },
  
  gridContainer: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  card: { width: '48%', marginBottom: 20 }, // 2열 그리드
  
  imageContainer: { position: 'relative', marginBottom: 10 },
  image: { width: '100%', aspectRatio: 1, borderRadius: 8, backgroundColor: '#F0F0F0' },
  stockBadge: { position: 'absolute', top: 8, left: 8, backgroundColor: 'rgba(231, 76, 60, 0.9)', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 4 },
  stockText: { color: '#fff', fontSize: 11, fontWeight: 'bold' },
  
  storeName: { fontSize: 12, color: '#888', marginBottom: 4 },
  productName: { fontSize: 14, fontWeight: 'bold', color: '#333', marginBottom: 6, lineHeight: 20 },
  
  priceContainer: { flexDirection: 'row', alignItems: 'center' },
  originalPrice: { fontSize: 12, color: '#999', textDecorationLine: 'line-through', marginRight: 6 },
  eventPrice: { fontSize: 15, fontWeight: 'bold', color: '#E74C3C' },
  
  emptyText: { color: '#888', fontSize: 15 }
});