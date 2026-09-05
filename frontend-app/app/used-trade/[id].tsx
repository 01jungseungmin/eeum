import React, { useCallback, useState } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, Dimensions, ActivityIndicator, Alert, NativeSyntheticEvent, NativeScrollEvent } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter, useLocalSearchParams, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { usedApi, UsedProductDetail } from '../../api/used';
import { favoriteApi } from '../../api/favorite';
import { userApi } from '../../api/user';

const { width } = Dimensions.get('window');

const STATUS_LABEL: Record<string, string> = {
  RESERVED: '예약중',
  SOLD: '거래완료',
};

// 작성 시각을 "5분 전" 형태로 보여준다. 하루가 넘어가면 날짜로 끊는다.
const formatTimeAgo = (iso?: string) => {
  if (!iso) return '';

  const created = new Date(iso).getTime();
  if (Number.isNaN(created)) return '';

  const diffMin = Math.floor((Date.now() - created) / 60000);
  if (diffMin < 1) return '방금 전';
  if (diffMin < 60) return `${diffMin}분 전`;
  if (diffMin < 60 * 24) return `${Math.floor(diffMin / 60)}시간 전`;
  if (diffMin < 60 * 24 * 7) return `${Math.floor(diffMin / (60 * 24))}일 전`;
  return iso.substring(0, 10);
};

const formatPrice = (product: UsedProductDetail) => {
  if (product.priceType === 'FREE') return '나눔';
  if (product.priceType === 'NEGOTIABLE') return '가격제안';
  return `${(product.price ?? 0).toLocaleString()}원`;
};

export default function UsedTradeDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();
  const usedProductId = Number(id);

  const [product, setProduct] = useState<UsedProductDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const [imageIndex, setImageIndex] = useState(0);
  const [favorited, setFavorited] = useState(false);
  const [favoriteCount, setFavoriteCount] = useState(0);
  const [isToggling, setIsToggling] = useState(false);
  // 비회원이면 null. 판매자 본인인지 가려서 찜·채팅 버튼을 다르게 보여준다.
  const [myAccountId, setMyAccountId] = useState<number | null>(null);

  // 목록에서 찜을 바꾸고 돌아올 수 있어 포커스마다 다시 맞춘다.
  useFocusEffect(
    useCallback(() => {
      let isActive = true;

      const fetchDetail = async () => {
        if (!usedProductId || Number.isNaN(usedProductId)) {
          setHasError(true);
          setIsLoading(false);
          return;
        }

        setIsLoading(true);
        try {
          const detail = await usedApi.getUsedProduct(usedProductId);
          if (!isActive) return;

          setProduct(detail);
          setFavoriteCount(detail.favoriteCount ?? 0);
          setHasError(false);
        } catch (error) {
          console.error('중고거래 상세 조회 실패:', error);
          if (isActive) setHasError(true);
        } finally {
          if (isActive) setIsLoading(false);
        }

        // 찜 여부는 로그인 상태에서만 의미가 있다. 비회원은 401이 나므로 하트만 비워둔다.
        try {
          const check = await favoriteApi.checkFavorite('USED_PRODUCT', usedProductId);
          if (isActive) setFavorited(check.favorited);
        } catch {
          if (isActive) setFavorited(false);
        }

        // 비회원이면 401이 난다. 그때는 로그인 안 된 상태로 두면 된다.
        try {
          const me = await userApi.getMyInfo();
          if (isActive) setMyAccountId(me.accountId ?? null);
        } catch {
          if (isActive) setMyAccountId(null);
        }
      };

      fetchDetail();

      return () => { isActive = false; };
    }, [usedProductId])
  );

  const handleScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    setImageIndex(Math.round(e.nativeEvent.contentOffset.x / width));
  };

  // 로그인이 필요한 액션 앞에서 공통으로 부른다.
  const promptLogin = () => {
    Alert.alert('로그인 필요', '로그인 후 이용할 수 있습니다.', [
      { text: '취소', style: 'cancel' },
      { text: '로그인', onPress: () => router.push('/(auth)/login') },
    ]);
  };

  const handleToggleFavorite = async () => {
    if (!product || isToggling) return;
    if (myAccountId === null) {
      promptLogin();
      return;
    }

    setIsToggling(true);
    // 서버 응답을 기다리지 않고 먼저 반영하고, 실패하면 되돌린다.
    const prevFavorited = favorited;
    const prevCount = favoriteCount;
    setFavorited(!prevFavorited);
    setFavoriteCount(prevCount + (prevFavorited ? -1 : 1));

    try {
      const res = await favoriteApi.toggleFavorite('USED_PRODUCT', product.usedProductId);
      setFavorited(res.favorited);
      if (res.favoriteCount !== null) setFavoriteCount(res.favoriteCount);
    } catch (error) {
      setFavorited(prevFavorited);
      setFavoriteCount(prevCount);
      console.error('찜 처리 실패:', error);
      Alert.alert('알림', '찜 처리에 실패했습니다. 로그인 상태를 확인해주세요.');
    } finally {
      setIsToggling(false);
    }
  };

  const handleChat = () => {
    if (!product) return;
    if (myAccountId === null) {
      promptLogin();
      return;
    }
    if (product.status === 'SOLD') {
      Alert.alert('알림', '이미 거래가 완료된 상품입니다.');
      return;
    }
    // 판매자와의 1:1 채팅방 생성 API가 아직 없다(현재는 그룹방 생성만 제공).
    Alert.alert('알림', '판매자와의 채팅 기능은 준비 중입니다.');
  };

  if (isLoading && !product) {
    return (
      <SafeAreaView style={[styles.container, styles.center]}>
        <Stack.Screen options={{ headerShown: false }} />
        <ActivityIndicator size="large" color="#00A859" />
      </SafeAreaView>
    );
  }

  if (hasError || !product) {
    return (
      <SafeAreaView style={styles.container}>
        <Stack.Screen options={{ headerShown: false }} />
        <View style={styles.header}>
          <TouchableOpacity onPress={() => router.back()} style={styles.iconButton}>
            <Ionicons name="chevron-back" size={26} color="#333" />
          </TouchableOpacity>
        </View>
        <View style={[styles.container, styles.center]}>
          <Text style={styles.emptyText}>게시글을 불러올 수 없습니다.</Text>
        </View>
      </SafeAreaView>
    );
  }

  const images = product.images ?? [];
  const statusLabel = STATUS_LABEL[product.status];
  const isMine = myAccountId !== null && myAccountId === product.sellerId;

  return (
    <SafeAreaView style={styles.container}>
      <Stack.Screen options={{ headerShown: false }} />

      {/* 1. 상단 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.iconButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <View style={styles.headerRight}>
          <TouchableOpacity style={styles.iconButton}>
            <Ionicons name="arrow-redo-outline" size={24} color="#333" />
          </TouchableOpacity>
          <TouchableOpacity style={styles.iconButton}>
            <Ionicons name="ellipsis-vertical" size={24} color="#333" />
          </TouchableOpacity>
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        {/* 2. 상품 이미지 영역 */}
        <View style={styles.imageContainer}>
          {images.length > 0 ? (
            <ScrollView
              horizontal
              pagingEnabled
              showsHorizontalScrollIndicator={false}
              onMomentumScrollEnd={handleScroll}
            >
              {images.map((image) => (
                <Image
                  key={image.imageId}
                  source={{ uri: image.imageUrl }}
                  style={styles.productImage}
                  resizeMode="cover"
                />
              ))}
            </ScrollView>
          ) : (
            <View style={[styles.productImage, styles.center, styles.emptyImage]}>
              <Ionicons name="image-outline" size={48} color="#CCC" />
            </View>
          )}

          {/* 이미지 인덱스 뱃지 */}
          {images.length > 1 && (
            <View style={styles.imageBadge}>
              <Text style={styles.imageBadgeText}>{imageIndex + 1} / {images.length}</Text>
            </View>
          )}

          {statusLabel && (
            <View style={styles.statusBadge}>
              <Text style={styles.statusBadgeText}>{statusLabel}</Text>
            </View>
          )}
        </View>

        {/* 3. 판매자 정보 영역 */}
        <View style={styles.sellerSection}>
          <View style={styles.avatar} />
          <View style={styles.sellerInfo}>
            <Text style={styles.sellerName}>{product.sellerNickname || '알 수 없음'}</Text>
            <Text style={styles.sellerMeta}>{product.regionName || '동네 미설정'}</Text>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 4. 상품 기본 정보 영역 */}
        <View style={styles.productSection}>
          {!!product.categoryName && (
            <View style={styles.categoryBadge}>
              <Text style={styles.categoryBadgeText}>{product.categoryName}</Text>
            </View>
          )}

          <Text style={styles.title}>{product.title}</Text>

          <View style={styles.metaRow}>
            <Ionicons name="time-outline" size={14} color="#999" />
            <Text style={styles.metaText}>{formatTimeAgo(product.createdAt)}</Text>
            <Text style={styles.metaDot}>·</Text>

            <Ionicons name="eye-outline" size={14} color="#999" />
            <Text style={styles.metaText}>{product.viewCount ?? 0}</Text>
            <Text style={styles.metaDot}>·</Text>

            <Ionicons name="heart-outline" size={14} color="#999" />
            <Text style={styles.metaText}>{favoriteCount}</Text>
          </View>

          <Text style={styles.price}>{formatPrice(product)}</Text>

          <View style={styles.locationRow}>
            <Ionicons name="location-outline" size={16} color="#00A859" />
            <Text style={styles.locationText}>{product.regionName || '동네 정보 없음'}</Text>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 5. 상품 상세 설명 영역 */}
        <View style={styles.descriptionSection}>
          <Text style={styles.sectionTitle}>상품 정보</Text>
          <Text style={styles.descriptionText}>{product.content}</Text>
        </View>
      </ScrollView>

      {/* 6. 하단 고정 탭바 (찜하기 & 채팅하기) */}
      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.heartButton} onPress={handleToggleFavorite} disabled={isToggling}>
          <Ionicons
            name={favorited ? 'heart' : 'heart-outline'}
            size={24}
            color={favorited ? '#FF3B30' : '#999'}
          />
        </TouchableOpacity>
        {isMine ? (
          <View style={[styles.chatButton, styles.chatButtonDisabled]}>
            <Text style={styles.chatButtonText}>내가 등록한 상품입니다</Text>
          </View>
        ) : (
          <TouchableOpacity style={styles.chatButton} onPress={handleChat}>
            <Text style={styles.chatButtonText}>채팅하기</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  center: { justifyContent: 'center', alignItems: 'center' },
  emptyText: { fontSize: 14, color: '#999' },

  // 헤더
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 8, height: 56, backgroundColor: '#FFF', zIndex: 10 },
  headerRight: { flexDirection: 'row', alignItems: 'center' },
  iconButton: { padding: 8 },

  scrollContent: { paddingBottom: 100 },

  // 이미지 영역
  imageContainer: { width: width, height: width, position: 'relative' }, // 1:1 비율
  productImage: { width: width, height: '100%' },
  emptyImage: { backgroundColor: '#F5F5F5' },
  imageBadge: { position: 'absolute', bottom: 16, right: 16, backgroundColor: 'rgba(0,0,0,0.6)', paddingHorizontal: 12, paddingVertical: 4, borderRadius: 14 },
  imageBadgeText: { color: '#FFF', fontSize: 12, fontWeight: 'bold' },
  statusBadge: { position: 'absolute', top: 16, left: 16, backgroundColor: 'rgba(0,0,0,0.7)', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6 },
  statusBadgeText: { color: '#FFF', fontSize: 13, fontWeight: 'bold' },

  // 판매자 영역
  sellerSection: { flexDirection: 'row', alignItems: 'center', padding: 20 },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#D9D9D9', marginRight: 12 },
  sellerInfo: { flex: 1 },
  sellerName: { fontSize: 15, fontWeight: 'bold', color: '#333', marginBottom: 2 },
  sellerMeta: { fontSize: 13, color: '#999' },

  divider: { height: 1, backgroundColor: '#F0F0F0', marginHorizontal: 20 },

  // 상품 정보 영역
  productSection: { padding: 20 },
  categoryBadge: { alignSelf: 'flex-start', backgroundColor: '#E8F5E9', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 4, marginBottom: 12 },
  categoryBadgeText: { color: '#00A859', fontSize: 12, fontWeight: 'bold' },
  title: { fontSize: 18, fontWeight: 'bold', color: '#333', marginBottom: 10 },
  metaRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 16 },
  metaText: { fontSize: 13, color: '#999', marginLeft: 4 },
  metaDot: { fontSize: 13, color: '#999', marginHorizontal: 6 },
  price: { fontSize: 20, fontWeight: 'bold', color: '#333', marginBottom: 12 },
  locationRow: { flexDirection: 'row', alignItems: 'center' },
  locationText: { fontSize: 14, color: '#333', marginLeft: 4 },

  // 상세 설명 영역
  descriptionSection: { padding: 20 },
  sectionTitle: { fontSize: 15, fontWeight: 'bold', color: '#333', marginBottom: 12 },
  descriptionText: { fontSize: 15, color: '#666', lineHeight: 24 },

  // 하단 탭바
  bottomBar: { position: 'absolute', bottom: 0, left: 0, right: 0, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 12, backgroundColor: '#FFF', borderTopWidth: 1, borderTopColor: '#F0F0F0', paddingBottom: 24 }, // 아이폰 하단 인디케이터 여백 고려
  heartButton: { width: 50, height: 50, borderRadius: 8, borderWidth: 1, borderColor: '#DDD', justifyContent: 'center', alignItems: 'center', marginRight: 12 },
  chatButton: { flex: 1, height: 50, backgroundColor: '#00A859', borderRadius: 8, justifyContent: 'center', alignItems: 'center' },
  chatButtonDisabled: { backgroundColor: '#BDBDBD' },
  chatButtonText: { color: '#FFF', fontSize: 16, fontWeight: 'bold' },
});
