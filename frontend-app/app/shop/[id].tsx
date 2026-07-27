import React, { useState, useEffect } from 'react';
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

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();

  const insets = useSafeAreaInsets(); 

  const [isLoading, setIsLoading] = useState(true);
  const [shopDetail, setShopDetail] = useState<any>(null);
  const [shopProducts, setShopProducts] = useState<any[]>([]);
  const [isFavorited, setIsFavorited] = useState<boolean>(false);
  const [favoriteCount, setFavoriteCount] = useState<number>(0);
  const [shopReviews, setShopReviews] = useState<any[]>([]);
  const [isVerified, setIsVerified] = useState<boolean>(false);

  const shopIdNum = typeof id === 'string' ? Number(id) : 1;

  useEffect(() => {
    const fetchShopData = async () => {
      try {
        setIsLoading(true);
        const [detailData, productsData, checkRes, countRes, reviewsRes, regionsRes] = await Promise.all([
          shopApi.getShopDetail(shopIdNum),
          shopApi.getShopProducts(shopIdNum),
          favoriteApi.checkFavorite('STORE', shopIdNum).catch(() => null),
          favoriteApi.getFavoriteCount('STORE', shopIdNum).catch(() => null),
          reviewApi.getReviews(shopIdNum).catch(() => null),
          regionApi.getMyRegions().catch(() => null) 
        ]);

        setShopReviews(reviewsRes?.content || reviewsRes?.data || []);
        setShopDetail(detailData);
        setShopProducts(productsData || []);
        if (checkRes?.data?.data) setIsFavorited(checkRes.data.data.favorited);
        if (countRes?.data) setFavoriteCount(countRes.data.data);

        // 유연한 동네 인증 검사 로직
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
        console.log("❌ 에러:", e);
        Alert.alert("오류", "정보를 불러오지 못했습니다.");
        router.back();
      } finally {
        setIsLoading(false);
      }
    };

    if (shopIdNum) fetchShopData();
  }, [shopIdNum]);

  const handleToggleFavorite = async () => {
    try {
      const res = await favoriteApi.toggleFavorite('STORE', shopIdNum);
      const { favorited, favoriteCount: newCount } = res.data.data;
      setIsFavorited(favorited);
      setFavoriteCount(newCount);
    } catch (error) {
      Alert.alert("알림", "찜 상태를 변경할 수 없습니다.");
    }
  };

  const handleGroupChat = async () => {
    if (!isVerified) {
      Alert.alert('동네 인증 필요', '이 상점의 단체 채팅방에 참여하려면 마이페이지에서 대표 동네를 인증해주세요.');
      return;
    }

    const roomId = shopDetail?.chatRoomId; 

    if (!roomId /* || !shopDetail?.chatRoomExists */) {
      Alert.alert('알림', '아직 이 상점의 단체 채팅방이 개설되지 않았습니다.');
      return;
    }

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

          {/* 상단 소개 바로 밑으로 이동한 예약 버튼 (식당/상점 구분 없이 모두 노출) */}
          <TouchableOpacity
            style={styles.contentReserveBtn}
            activeOpacity={0.7}
            onPress={() => {
              if (!isVerified) {
                Alert.alert('동네 인증 필요', '예약하려면 마이페이지에서 대표 동네를 인증해주세요.');
                return;
              }
              router.push({
                pathname: '/restaurant/reservation' as any,
                params: { storeId: shopDetail.storeId }
              });
            }}
          >
            <Ionicons name="calendar-outline" size={16} color="#333" style={{ marginRight: 6 }} />
            <Text fontWeight="bold" style={styles.contentReserveBtnText}>상점 방문 예약하기</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        {/* 메뉴 섹션 */}
        <View style={styles.menuSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>메뉴</Text>
          {shopProducts.map((menu: any) => (
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
          ))}
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
                {review.images && review.images.length > 0 && (
                  <Image source={{ uri: review.images[0].imageUrl }} style={styles.reviewImage} />
                )}
              </View>
            ))
          )}
          
          {shopReviews.length > 0 && (
            <TouchableOpacity 
              style={styles.moreReviewBtn}
              onPress={() => router.push({
                pathname: '/review/list' as any,
                params: { storeId: shopDetail.storeId }
              })}
            >
              <Text style={styles.moreReviewBtnText}>리뷰 더 보기</Text>
              <Ionicons name="chevron-forward" size={16} color="#666" />
            </TouchableOpacity>
          )}
        </View>

      </ScrollView>

      {/* 하단 고정 버튼 영역 */}
      <View style={[styles.bottomBar, { paddingBottom: Math.max(insets.bottom, 15) + 10 }]}>
        <View style={{ width: '100%' }}>
          
          {/* 1. 상단 메인 액션 버튼 (조건 없이 항상 장바구니로 통일) */}
          <TouchableOpacity
            style={styles.cartButton}
            activeOpacity={0.8}
            onPress={() => {
              if (!isVerified) {
                Alert.alert('동네 인증 필요', '상품을 구매하려면 대표 동네를 인증해주세요.');
                return;
              }
              router.push('/cart');
            }}
          >
            <Ionicons name="cart-outline" size={18} color="#FFF" style={{ marginRight: 6 }} />
            <Text fontWeight="bold" style={styles.cartButtonText}>장바구니 보기</Text>
          </TouchableOpacity>

          {/* 2. 하단 2분할 버튼 (문의하기 & 단체 채팅) */}
          <View style={styles.rowButtons}>
            <TouchableOpacity 
              style={styles.halfButton} 
              activeOpacity={0.7}
              onPress={() => router.push(`/inquiry/write?storeId=${shopIdNum}` as any)}
            >
              <Ionicons name="chatbubble-outline" size={18} color="#1B854A" style={{ marginRight: 6 }} />
              <Text style={styles.halfButtonText}>문의하기</Text>
            </TouchableOpacity>

            <TouchableOpacity 
              style={styles.halfButton} 
              activeOpacity={0.7} 
              onPress={handleGroupChat}
            >
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

  menuSection: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 20 },
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
  reviewImage: { width: 80, height: 80, borderRadius: 8, marginTop: 10 },
  moreReviewBtn: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 15 },
  moreReviewBtnText: { color: '#666', fontSize: 14, marginRight: 4, fontWeight: '500' },

  bottomBar: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: 1, borderTopColor: '#EAEAEA', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  
  // 장바구니 버튼 스타일
  cartButton: { flexDirection: 'row', backgroundColor: '#1B854A', paddingVertical: 14, borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginBottom: 10 },
  cartButtonText: { color: '#FFF', fontSize: 15 },
  
  rowButtons: { flexDirection: 'row', justifyContent: 'space-between' },
  halfButton: { flex: 1, flexDirection: 'row', backgroundColor: '#FFF', borderWidth: 1, borderColor: '#1B854A', paddingVertical: 12, borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginHorizontal: 4 },
  halfButtonText: { color: '#1B854A', fontSize: 14 },
});