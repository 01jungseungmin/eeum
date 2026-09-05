import React, { useState, useEffect, useMemo } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { shopApi } from '../../api/shop';
import { favoriteApi } from '../../api/favorite';
import { reviewApi } from '../../api/review';
import { regionApi } from '@/api/region';
import { chatApi } from '../../api/chat';

const { width } = Dimensions.get('window');

const getProductCategoryId = (product: any) => {
  const id = 
    product?.productCategoryId ??
    product?.categoryId ??
    product?.productCategory?.productCategoryId ??
    product?.productCategory?.id ??
    null;
    
  // 값이 있으면 무조건 숫자로 변환해서 반환 (문자열 "1" === 숫자 1 비교 실패 방지)
  return id !== null ? Number(id) : null; 
};

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();

  const insets = useSafeAreaInsets();

  const [isLoading, setIsLoading] = useState(true);
  const [shopDetail, setShopDetail] = useState<any>(null);
  const [shopProducts, setShopProducts] = useState<any[]>([]);
  
  // 💡 이벤트 상품 상태 추가
  const [eventProducts, setEventProducts] = useState<any[]>([]); 
  
  const [isFavorited, setIsFavorited] = useState<boolean>(false);
  const [favoriteCount, setFavoriteCount] = useState<number>(0);
  const [shopReviews, setShopReviews] = useState<any[]>([]);
  const [shopNotices, setShopNotices] = useState<any[]>([]);
  const [productCategories, setProductCategories] = useState<any[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [isVerified, setIsVerified] = useState<boolean>(false);

  const shopIdNum = typeof id === 'string' ? Number(id) : 1;

  useEffect(() => {
    const fetchShopData = async () => {
      try {
        setIsLoading(true);
        // 💡 Promise.all 배열에 getEventProducts 추가 (병렬로 빠르게 가져오기)
        const [
          detailData, productsData, checkRes, countRes, 
          reviewsRes, regionsRes, noticesRes, categoriesRes, eventRes 
        ] = await Promise.all([
          shopApi.getShopDetail(shopIdNum),
          shopApi.getShopProducts(shopIdNum),
          favoriteApi.checkFavorite('STORE', shopIdNum).catch(() => null),
          favoriteApi.getFavoriteCount('STORE', shopIdNum).catch(() => null),
          reviewApi.getReviews(shopIdNum).catch(() => null),
          regionApi.getMyRegions().catch(() => null),
          shopApi.getShopNotices(shopIdNum).catch(() => []),
          shopApi.getShopProductCategories(shopIdNum).catch(() => []),
          shopApi.getEventProducts(shopIdNum).catch(() => []) // ✨ 추가
        ]);

        setShopReviews(reviewsRes?.content || reviewsRes?.data || []);
        setShopDetail(detailData);
        setShopProducts(productsData || []);

        console.log("🔍 백엔드가 주는 일반 상품 1개 데이터:", productsData?.[0]);
        
        // 💡 이벤트 상품 데이터 세팅
        setEventProducts(eventRes?.data || eventRes || []);

        if (checkRes) setIsFavorited(checkRes.favorited);
        if (countRes != null) setFavoriteCount(countRes);

        setShopNotices(Array.isArray(noticesRes) ? noticesRes : (noticesRes?.content ?? []));

        const rawCategories = Array.isArray(categoriesRes) ? categoriesRes : (categoriesRes?.content ?? []);
        setProductCategories(
          rawCategories
            .filter((c: any) => c.active !== false)
            .sort((a: any, b: any) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0))
        );

        if (regionsRes?.data) {
          const primaryRegion = regionsRes.data.find((r: any) => r.isPrimary === true);
          const isPrimaryVerified = primaryRegion?.verified === true || primaryRegion?.isVerified === true;
          const targetRegionId = detailData?.regionId || detailData?.region?.regionId;
          const isStoreRegionVerified = regionsRes.data.some(
            (r: any) => r.regionId === targetRegionId && (r.verified === true || r.isVerified === true)
          );
          setIsVerified(isPrimaryVerified || isStoreRegionVerified);
        }
      } catch (e) {
        Alert.alert("오류", "정보를 불러오지 못했습니다.");
        router.back();
      } finally {
        setIsLoading(false);
      }
    };

    if (shopIdNum) fetchShopData();
  }, [shopIdNum]);

  // 💡 진행 중이고 재고가 남아있는 이벤트 상품만 필터링
  const activeEventProducts = useMemo(() => {
    return eventProducts.filter(p => p.ongoing !== false && p.remainingStock > 0);
  }, [eventProducts]);

  // 💡 선택된 카테고리에 따라 상품 필터링 (단, 진행 중인 이벤트 상품은 아래에서 제외!)
  const filteredProducts = useMemo(() => {
    // 1. 이벤트 중인 상품들의 ID 목록만 따로 뽑아냅니다.
    const eventProductIds = activeEventProducts.map(ep => 
      ep.productId || ep.product?.productId || ep.product?.id || ep.id
    );

    // 2. 카테고리 필터링 적용
    let result = shopProducts;
    if (selectedCategoryId !== null) {
      result = result.filter((p) => getProductCategoryId(p) === selectedCategoryId);
    }

    // 3. 일반 메뉴 리스트에서 이벤트 상품 ID를 가진 녀석들은 제외!
    return result.filter(p => {
      const currentId = p.productId || p.id;
      return !eventProductIds.includes(currentId);
    });
  }, [shopProducts, selectedCategoryId, activeEventProducts]);


  const handleToggleFavorite = async () => {
    try {
      const { favorited, favoriteCount: newCount } = await favoriteApi.toggleFavorite('STORE', shopIdNum);
      setIsFavorited(favorited);

      // 비공개 대상 해제 시 서버가 favoriteCount를 주지 않는다. 그때는 직접 보정한다.
      setFavoriteCount(prev =>
        newCount ?? Math.max(0, prev + (favorited ? 1 : -1))
      );
    } catch (error) {
      Alert.alert("알림", "찜 상태를 변경할 수 없습니다.");
    }
  };

  const handleGroupChat = async () => {
    if (!isVerified) return Alert.alert('동네 인증 필요', '단체 채팅방에 참여하려면 대표 동네를 인증해주세요.');
    const roomId = shopDetail?.chatRoomId;
    if (!roomId) return Alert.alert('알림', '아직 단체 채팅방이 개설되지 않았습니다.');
    try {
      await chatApi.joinRoom(roomId);
      router.push(`/chat/${roomId}` as any);
    } catch (error: any) {
      if (error.response?.status === 409 || error.response?.status === 400) {
        router.push(`/chat/${roomId}` as any);
      } else {
        Alert.alert('오류', '단체 채팅방에 입장할 수 없습니다.');
      }
    }
  };

  if (isLoading) {
    return (
      <View style={{flex:1, justifyContent:'center', alignItems:'center', backgroundColor: '#fff'}}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  if (!shopDetail) return null;

  const categoryName = shopDetail.categoryName || '기타';
  const coverImageUrl = shopDetail.images?.[0]?.imageUrl || 'https://via.placeholder.com/600x400/E8F5E9/00A859?text=Cover';
  const isRestaurant = shopDetail.categoryId === 1 || shopDetail.categoryId === 2;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 150 }}>
        {/* 커버 이미지 */}
        <View style={styles.coverContainer}>
          <Image source={{ uri: coverImageUrl }} style={styles.coverImg} />
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}>
            <Ionicons name="chevron-back" size={28} color="#fff" />
          </TouchableOpacity>
        </View>

        <View style={styles.mainInfo}>
          <View style={styles.categoryBadge}><Text style={styles.categoryText}>{categoryName}</Text></View>
          <View style={styles.nameRow}>
            <Text fontWeight="bold" style={styles.shopName}>{shopDetail.name}</Text>
            <View style={styles.ratingRow}>
              <TouchableOpacity onPress={handleToggleFavorite} style={{ flexDirection: 'row', alignItems: 'center', marginRight: 12 }}>
                <Ionicons name={isFavorited ? "heart" : "heart-outline"} size={22} color={isFavorited ? "#FF5252" : "#999"} />
                <Text style={{ marginLeft: 4, fontSize: 16, color: '#333' }}>{favoriteCount}</Text>
              </TouchableOpacity>
              <Ionicons name="star" size={18} color="#FFD700" />
              <Text fontWeight="bold" style={styles.ratingText}>{shopDetail.rating?.toFixed(1) || '0.0'}</Text>
            </View>
          </View>
          <View style={styles.contactRow}><Ionicons name="location-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.address}</Text></View>
          <View style={styles.contactRow}><Ionicons name="call-outline" size={16} color="#888" /><Text style={styles.contactText}>{shopDetail.phone}</Text></View>

          <TouchableOpacity
            style={styles.contentReserveBtn}
            activeOpacity={0.7}
            onPress={() => {
              if (!isVerified) return Alert.alert('동네 인증 필요', '예약하려면 대표 동네를 인증해주세요.');
              router.push({ pathname: '/restaurant/reservation' as any, params: { storeId: shopDetail.storeId } });
            }}
          >
            <Ionicons name="calendar-outline" size={16} color="#333" style={{ marginRight: 6 }} />
            <Text fontWeight="bold" style={styles.contentReserveBtnText}>상점 방문 예약하기</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        {shopNotices.length > 0 && (
          <>
            <View style={styles.noticeSection}>
              <Text fontWeight="bold" style={styles.sectionTitle}>상점 공지</Text>
              {shopNotices.map((notice: any, index: number) => (
                <View key={notice.noticeId ?? notice.id ?? index} style={styles.noticeCard}>
                  <View style={styles.noticeTitleRow}>
                    <Ionicons name="megaphone-outline" size={16} color="#00A859" />
                    <Text fontWeight="bold" style={styles.noticeTitle} numberOfLines={1}>{notice.title}</Text>
                  </View>
                  <Text style={styles.noticeContent} numberOfLines={3}>{notice.content}</Text>
                </View>
              ))}
            </View>
            <View style={styles.divider} />
          </>
        )}

        {/* 💡 이벤트 상품 섹션 (진행 중인 이벤트가 있을 때만 노출) */}
        {activeEventProducts.length > 0 && (
          <>
            <View style={styles.eventSection}>
              <Text fontWeight="bold" style={[styles.sectionTitle, { color: '#FF5252' }]}>🔥 진행 중인 이벤트</Text>
              
              {activeEventProducts.map((eventProd: any, index: number) => {
                // 💡 1. API 명세서에 맞춘 정확한 변수명 매핑
                const pId = eventProd.productId;
                const pName = eventProd.productName || '상품명 없음';
                
                // 💡 2. 가격 및 재고 매핑
                const pPrice = eventProd.originalPrice || 0;
                const pEventPrice = eventProd.eventPrice || 0;
                const pStock = eventProd.remainingStock || 0;
                
                // 💡 3. 이미지 방어 로직
                const rawImage = eventProd.thumbnailUrl;
                const isValidImage = rawImage && rawImage !== 'null' && rawImage.trim() !== '';

                // 💡 4. API에 할인율이 없으므로 직접 계산 (원가보다 이벤트가가 저렴할 때만)
                const discountRate = pPrice > 0 && pEventPrice > 0 && pPrice > pEventPrice
                  ? Math.round(((pPrice - pEventPrice) / pPrice) * 100)
                  : 0;

                return (
                  <TouchableOpacity
                    key={`event-${pId || index}`}
                    style={styles.eventCard}
                    onPress={() => router.push({
                      pathname: `/product/${pId}` as any,
                      params: { isRestaurant: isRestaurant ? 'true' : 'false' }
                    })}
                  >
                    <View style={styles.menuTextContainer}>
                      <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 5 }}>
                        <Text fontWeight="bold" style={styles.menuName}>{pName}</Text>
                        <View style={styles.stockBadge}>
                          <Text style={styles.stockText}>남은 수량 {pStock}개</Text>
                        </View>
                      </View>
                      
                      {/* API에 description이 포함되어 있지 않으므로 설명란은 제외했습니다 */}
                      
                      <View style={styles.priceRow}>
                        {/* 직접 계산한 할인율 노출 */}
                        {discountRate > 0 ? (
                          <Text style={styles.discountRate}>{discountRate}%</Text>
                        ) : null}
                        
                        <Text fontWeight="bold" style={styles.menuPrice}>
                          {pEventPrice > 0 ? pEventPrice.toLocaleString() : pPrice.toLocaleString()}원
                        </Text>
                        
                        {/* 이벤트가가 있고, 원가보다 쌀 때만 취소선 가격 노출 */}
                        {pEventPrice > 0 && pPrice > pEventPrice ? (
                          <Text style={styles.originalPrice}>{pPrice.toLocaleString()}원</Text>
                        ) : null}
                      </View>
                    </View>

                    {/* 이미지 안전 렌더링 */}
                    {isValidImage ? (
                      <Image source={{ uri: rawImage }} style={styles.menuImg} />
                    ) : (
                      <View style={[styles.menuImg, { backgroundColor: '#F0F0F0', justifyContent: 'center', alignItems: 'center' }]}>
                        <Ionicons name="image-outline" size={28} color="#CCC" />
                        <Text style={{ fontSize: 10, color: '#999', marginTop: 4 }}>No Image</Text>
                      </View>
                    )}
                    
                  </TouchableOpacity>
                );
              })}
            </View>
            <View style={styles.divider} />
          </>
        )}

        {/* 일반 메뉴 섹션 */}
        <View style={styles.menuSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>메뉴</Text>
          {productCategories.length > 0 && (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow} style={styles.chipScroll}>
              <TouchableOpacity
                style={[styles.chip, selectedCategoryId === null && styles.chipActive]}
                onPress={() => setSelectedCategoryId(null)}
              >
                <Text style={[styles.chipText, selectedCategoryId === null && styles.chipTextActive]}>
                  전체 {shopProducts.length}
                </Text>
              </TouchableOpacity>
              {productCategories.map((cat: any) => {
                const isActive = selectedCategoryId === cat.productCategoryId;
                return (
                  <TouchableOpacity
                    key={cat.productCategoryId}
                    style={[styles.chip, isActive && styles.chipActive]}
                    onPress={() => setSelectedCategoryId(cat.productCategoryId)}
                  >
                    <Text style={[styles.chipText, isActive && styles.chipTextActive]}>
                      {cat.name}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          )}

          {filteredProducts.length === 0 ? (
            <Text style={styles.emptyMenuText}>해당 카테고리에 등록된 상품이 없습니다.</Text>
          ) : (
            filteredProducts.map((menu: any) => (
              <TouchableOpacity
                key={menu.productId}
                style={styles.menuCard}
                onPress={() => router.push({
                  pathname: `/product/${menu.productId}` as any,
                  params: { isRestaurant: isRestaurant ? 'true' : 'false' }
                })}
              >
                <View style={styles.menuTextContainer}>
                  <Text fontWeight="bold" style={styles.menuName}>{menu.name}</Text>
                  <Text style={styles.menuDesc} numberOfLines={2}>{menu.description}</Text>
                  <Text fontWeight="bold" style={styles.menuPrice}>{menu.price?.toLocaleString()}원</Text>
                </View>
                {menu.thumbnailUrl && <Image source={{ uri: menu.thumbnailUrl }} style={styles.menuImg} />}
              </TouchableOpacity>
            ))
          )}
        </View>

        <View style={styles.divider} />

        {/* 리뷰 섹션 */}
        <View style={styles.reviewSection}>
          <View style={styles.reviewHeader}>
            <Text fontWeight="bold" style={styles.sectionTitle}>상점 리뷰</Text>
          </View>
          {shopReviews.length === 0 ? (
            <Text style={styles.emptyReviewText}>아직 등록된 리뷰가 없습니다.</Text>
          ) : (
            shopReviews.slice(0, 3).map((review: any) => (
              <View key={review.storereviewId} style={styles.reviewCard}>
                <View style={styles.reviewUserRow}>
                  <Ionicons name="star" size={14} color="#FFD700" />
                  <Text style={styles.reviewRatingText}>{review.rating}</Text>
                  <Text style={styles.reviewWriterText}>{review.nickname || '익명'}</Text>
                </View>
                <Text style={styles.reviewContentText}>{review.content}</Text>
              </View>
            ))
          )}
        </View>
      </ScrollView>

      {/* 하단 고정 버튼 영역 */}
      <View style={[styles.bottomBar, { paddingBottom: Math.max(insets.bottom, 15) + 10 }]}>
        <View style={{ width: '100%' }}>
          <TouchableOpacity
            style={styles.cartButton}
            onPress={() => {
              if (!isVerified) return Alert.alert('동네 인증 필요', '상품을 구매하려면 대표 동네를 인증해주세요.');
              router.push('/cart');
            }}
          >
            <Ionicons name="cart-outline" size={18} color="#FFF" style={{ marginRight: 6 }} />
            <Text fontWeight="bold" style={styles.cartButtonText}>장바구니 보기</Text>
          </TouchableOpacity>

          <View style={styles.rowButtons}>
            <TouchableOpacity style={styles.halfButton} onPress={() => router.push(`/inquiry/write?storeId=${shopIdNum}` as any)}>
              <Ionicons name="chatbubble-outline" size={18} color="#1B854A" style={{ marginRight: 6 }} />
              <Text style={styles.halfButtonText}>문의하기</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.halfButton} onPress={handleGroupChat}>
              <Ionicons name="chatbubbles-outline" size={18} color="#1B854A" style={{ marginRight: 6 }} />
              <Text style={styles.halfButtonText}>단체 채팅</Text>
            </TouchableOpacity>
          </View>
        </View>
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
  contactRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  contactText: { fontSize: 14, color: '#666', marginLeft: 10 },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  contentReserveBtn: { flexDirection: 'row', backgroundColor: '#F9F9F9', borderWidth: 1, borderColor: '#EAEAEA', paddingVertical: 12, borderRadius: 6, justifyContent: 'center', alignItems: 'center', marginTop: 15 },
  contentReserveBtnText: { color: '#333', fontSize: 14 },

  noticeSection: { padding: 20 },
  noticeCard: { backgroundColor: '#F8FBF9', borderRadius: 8, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: '#E8F5E9' },
  noticeTitleRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  noticeTitle: { fontSize: 15, color: '#333', marginLeft: 6, flex: 1 },
  noticeContent: { fontSize: 13, color: '#666', lineHeight: 20 },

  // ✨ 이벤트 상품 전용 스타일 추가
  eventSection: { padding: 20, backgroundColor: '#FFF5F5' }, // 살짝 붉은 배경으로 강조
  eventCard: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#FDECEC' },
  stockBadge: { backgroundColor: '#FF5252', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginLeft: 8 },
  stockText: { color: '#FFF', fontSize: 10, fontWeight: 'bold' },
  priceRow: { flexDirection: 'row', alignItems: 'center', marginTop: 4 },
  discountRate: { color: '#FF5252', fontSize: 16, fontWeight: 'bold', marginRight: 6 },
  originalPrice: { color: '#999', fontSize: 13, textDecorationLine: 'line-through', marginLeft: 6 },

  menuSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 20 },
  chipScroll: { marginBottom: 8, marginHorizontal: -20 },
  chipRow: { paddingHorizontal: 20, paddingBottom: 4 },
  chip: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20, borderWidth: 1, borderColor: '#E0E0E0', backgroundColor: '#FFF', marginRight: 8 },
  chipActive: { backgroundColor: '#00A859', borderColor: '#00A859' },
  chipText: { fontSize: 13, color: '#666' },
  chipTextActive: { color: '#FFF', fontWeight: 'bold' },
  emptyMenuText: { color: '#888', textAlign: 'center', paddingVertical: 30, fontSize: 14 },
  menuCard: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  menuTextContainer: { flex: 1, paddingRight: 15 },
  menuName: { fontSize: 16, color: '#333', marginBottom: 5 },
  menuDesc: { fontSize: 13, color: '#888', marginBottom: 10 },
  menuPrice: { fontSize: 16, color: '#333' },
  menuImg: { width: 100, height: 100, borderRadius: 8 },

  reviewSection: { padding: 20 },
  reviewHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  emptyReviewText: { color: '#888', textAlign: 'center', paddingVertical: 20 },
  reviewCard: { marginBottom: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0', paddingBottom: 15 },
  reviewUserRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 5 },
  reviewRatingText: { fontWeight: 'bold', marginLeft: 4, fontSize: 14, color: '#333' },
  reviewWriterText: { color: '#888', marginLeft: 10, fontSize: 12 },
  reviewContentText: { fontSize: 14, color: '#333', lineHeight: 20 },

  bottomBar: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: 1, borderTopColor: '#EAEAEA', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  cartButton: { flexDirection: 'row', backgroundColor: '#1B854A', paddingVertical: 14, borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginBottom: 10 },
  cartButtonText: { color: '#FFF', fontSize: 15 },
  rowButtons: { flexDirection: 'row', justifyContent: 'space-between' },
  halfButton: { flex: 1, flexDirection: 'row', backgroundColor: '#FFF', borderWidth: 1, borderColor: '#1B854A', paddingVertical: 12, borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginHorizontal: 4 },
  halfButtonText: { color: '#1B854A', fontSize: 14 },
});