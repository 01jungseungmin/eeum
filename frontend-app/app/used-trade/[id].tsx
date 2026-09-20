import React, { useCallback, useState } from 'react';
import { View, StyleSheet, Image, ScrollView, TouchableOpacity, useWindowDimensions, ActivityIndicator, Alert, NativeSyntheticEvent, NativeScrollEvent } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter, useLocalSearchParams, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { usedApi, UsedProductDetail } from '../../api/used';
import { favoriteApi } from '../../api/favorite';
import { userApi } from '../../api/user';
import { chatApi as usedChatApi } from '../../api/chat';
import { usedReviewApi, UsedReviewSummary } from '../../api/usedReview';
import { getApiErrorMessage } from '../../utils/apiError';
import TradePartnerPickerModal, { TradePartner } from '../../components/used/TradePartnerPickerModal';

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

  // 웹 데모는 PC 창 너비가 아니라 폰 틀 너비로 보정된 값이 들어온다. 모듈 로드 시점에
  // 한 번 재면 그 보정도, 창 크기 변경도 놓친다.
  const { width } = useWindowDimensions();

  const [product, setProduct] = useState<UsedProductDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const [imageIndex, setImageIndex] = useState(0);
  const [favorited, setFavorited] = useState(false);
  const [favoriteCount, setFavoriteCount] = useState(0);
  const [isToggling, setIsToggling] = useState(false);
  // 비회원이면 null. 판매자 본인인지 가려서 찜·채팅 버튼을 다르게 보여준다.
  const [myAccountId, setMyAccountId] = useState<number | null>(null);
  const [isChatting, setIsChatting] = useState(false);
  const [isChangingStatus, setIsChangingStatus] = useState(false);
  const [sellerSummary, setSellerSummary] = useState<UsedReviewSummary | null>(null);
  const [isPartnerPickerOpen, setIsPartnerPickerOpen] = useState(false);

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

          // 판매자 평판. 비회원도 볼 수 있고, 실패해도 상세는 그대로 떠야 한다.
          usedReviewApi
            .getSellerReviewSummary(detail.sellerId)
            .then((summary) => { if (isActive) setSellerSummary(summary); })
            .catch(() => { if (isActive) setSellerSummary(null); });
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

  // 핸들러들이 참조하므로 early return 앞에서 계산한다.
  const isMine = myAccountId !== null && product !== null && myAccountId === product.sellerId;
  // 판매완료 시 확정된 상대만 후기를 쓸 수 있다.
  const canWriteReview =
    product?.status === 'SOLD' &&
    myAccountId !== null &&
    product.buyerAccountId === myAccountId;

  const handleScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (width <= 0) return;
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

  const handleChat = async () => {
    if (!product || isChatting) return;
    if (myAccountId === null) {
      promptLogin();
      return;
    }
    if (product.status === 'SOLD') {
      Alert.alert('알림', '이미 거래가 완료된 상품입니다.');
      return;
    }

    setIsChatting(true);
    try {
      // 멱등이라 다시 눌러도 방이 새로 생기지 않는다.
      const room = await usedChatApi.createUsedProductInquiry(usedProductId);
      const roomId = room?.roomId;
      if (!roomId) throw new Error('roomId 없음');
      router.push(`/chat/${roomId}` as any);
    } catch (error) {
      // GPS 인증된 활동 지역이 없으면 서버가 막는다. 이유를 그대로 보여준다.
      Alert.alert('채팅을 시작할 수 없어요', getApiErrorMessage(error, '잠시 후 다시 시도해 주세요.'));
    } finally {
      setIsChatting(false);
    }
  };

  // ===================== 내 글 관리 =====================

  const handleEdit = () => {
    router.push(`/used-trade/write?editId=${usedProductId}` as any);
  };

  const handleDelete = () => {
    Alert.alert('게시글 삭제', '삭제한 글은 되돌릴 수 없어요. 삭제할까요?', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: async () => {
          try {
            await usedApi.deleteUsedProduct(usedProductId);
            router.back();
          } catch (error) {
            // 예약 중인 글은 예약을 먼저 취소해야 삭제된다 — 서버 문구를 그대로 보여준다.
            Alert.alert('삭제할 수 없어요', getApiErrorMessage(error, '잠시 후 다시 시도해 주세요.'));
          }
        },
      },
    ]);
  };

  const handleOpenMenu = () => {
    if (!isMine) return;
    Alert.alert('게시글 관리', undefined, [
      { text: '수정하기', onPress: handleEdit },
      { text: '삭제하기', style: 'destructive', onPress: handleDelete },
      { text: '닫기', style: 'cancel' },
    ]);
  };

  /** 상태 변경은 서버가 갱신된 상세를 돌려주므로 그걸 그대로 반영한다. */
  const runStatusChange = async (action: () => Promise<UsedProductDetail>) => {
    if (isChangingStatus) return;
    setIsChangingStatus(true);
    try {
      setProduct(await action());
    } catch (error) {
      Alert.alert('상태를 바꿀 수 없어요', getApiErrorMessage(error, '잠시 후 다시 시도해 주세요.'));
    } finally {
      setIsChangingStatus(false);
    }
  };

  // 거래 상대를 고르는 시트를 띄운다. 여기서 확정된 상대만 후기를 쓸 수 있다.
  const handleMarkSold = () => setIsPartnerPickerOpen(true);

  const handleConfirmPartner = (partner: TradePartner | null) => {
    setIsPartnerPickerOpen(false);
    // 상대를 안 고르면 buyerId를 생략한다 — 예약 때 지정한 상대가 있으면 그대로 유지된다.
    runStatusChange(() => usedApi.markSold(usedProductId, partner?.accountId));
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

  return (
    <SafeAreaView style={styles.container}>
      <Stack.Screen options={{ headerShown: false }} />

      {/* 1. 상단 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.iconButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <View style={styles.headerRight}>
          {/* 관리 메뉴는 내 글에서만 의미가 있다 */}
          {isMine && (
            <TouchableOpacity style={styles.iconButton} onPress={handleOpenMenu}>
              <Ionicons name="ellipsis-vertical" size={24} color="#333" />
            </TouchableOpacity>
          )}
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        {/* 2. 상품 이미지 영역 */}
        <View style={[styles.imageContainer, { width, height: width }]}>
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
                  style={[styles.productImage, { width }]}
                  resizeMode="cover"
                />
              ))}
            </ScrollView>
          ) : (
            <View style={[styles.productImage, styles.center, styles.emptyImage, { width }]}>
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
        <TouchableOpacity
          style={styles.sellerSection}
          onPress={() => router.push(`/used-trade/seller/${product.sellerId}` as any)}
        >
          <View style={styles.avatar} />
          <View style={styles.sellerInfo}>
            <Text style={styles.sellerName}>{product.sellerNickname || '알 수 없음'}</Text>
            <Text style={styles.sellerMeta}>{product.regionName || '동네 미설정'}</Text>
          </View>
          <View style={styles.sellerRating}>
            {sellerSummary && sellerSummary.reviewCount > 0 ? (
              <>
                <Ionicons name="star" size={14} color="#FFB800" />
                <Text style={styles.sellerRatingText}>
                  {Number(sellerSummary.averageRating ?? 0).toFixed(1)}
                </Text>
                <Text style={styles.sellerRatingCount}>({sellerSummary.reviewCount})</Text>
              </>
            ) : (
              <Text style={styles.sellerRatingCount}>후기 없음</Text>
            )}
            <Ionicons name="chevron-forward" size={16} color="#CCC" />
          </View>
        </TouchableOpacity>

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

          {product.tradeLocationName && (
            <View style={styles.locationRow}>
              <Ionicons name="pin-outline" size={16} color="#00A859" />
              <Text style={styles.locationText}>{product.tradeLocationName}</Text>
            </View>
          )}
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
          // 내 글에서는 채팅 대신 거래 상태를 바꾼다.
          product.status === 'SOLD' ? (
            <View style={[styles.chatButton, styles.chatButtonDisabled]}>
              <Text style={styles.chatButtonText}>거래완료된 상품입니다</Text>
            </View>
          ) : (
            <View style={styles.statusActions}>
              {product.status === 'RESERVED' ? (
                <TouchableOpacity
                  style={[styles.statusButton, styles.statusButtonOutline]}
                  disabled={isChangingStatus}
                  onPress={() => runStatusChange(() => usedApi.cancelReservation(usedProductId))}
                >
                  <Text style={styles.statusButtonOutlineText}>예약 취소</Text>
                </TouchableOpacity>
              ) : (
                <TouchableOpacity
                  style={[styles.statusButton, styles.statusButtonOutline]}
                  disabled={isChangingStatus}
                  onPress={() => runStatusChange(() => usedApi.reserve(usedProductId))}
                >
                  <Text style={styles.statusButtonOutlineText}>예약중으로</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity
                style={[styles.statusButton, styles.statusButtonPrimary]}
                disabled={isChangingStatus}
                onPress={handleMarkSold}
              >
                <Text style={styles.chatButtonText}>판매완료</Text>
              </TouchableOpacity>
            </View>
          )
        ) : canWriteReview ? (
          <TouchableOpacity
            style={styles.chatButton}
            onPress={() => router.push(`/used-trade/review/write?usedProductId=${usedProductId}` as any)}
          >
            <Text style={styles.chatButtonText}>거래 후기 남기기</Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity style={styles.chatButton} onPress={handleChat} disabled={isChatting}>
            <Text style={styles.chatButtonText}>{isChatting ? '여는 중...' : '채팅하기'}</Text>
          </TouchableOpacity>
        )}
      </View>

      <TradePartnerPickerModal
        visible={isPartnerPickerOpen}
        usedProductId={usedProductId}
        myAccountId={myAccountId}
        onClose={() => setIsPartnerPickerOpen(false)}
        onConfirm={handleConfirmPartner}
      />
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
  // 크기는 화면 너비에 따라 달라져 인라인으로 준다 (1:1 비율).
  imageContainer: { position: 'relative' },
  productImage: { height: '100%' },
  emptyImage: { backgroundColor: '#F5F5F5' },
  imageBadge: { position: 'absolute', bottom: 16, right: 16, backgroundColor: 'rgba(0,0,0,0.6)', paddingHorizontal: 12, paddingVertical: 4, borderRadius: 14 },
  imageBadgeText: { color: '#FFF', fontSize: 12, fontWeight: 'bold' },
  statusBadge: { position: 'absolute', top: 16, left: 16, backgroundColor: 'rgba(0,0,0,0.7)', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6 },
  statusBadgeText: { color: '#FFF', fontSize: 13, fontWeight: 'bold' },

  // 판매자 영역
  sellerSection: { flexDirection: 'row', alignItems: 'center', padding: 20 },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#D9D9D9', marginRight: 12 },
  sellerInfo: { flex: 1 },
  sellerRating: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  sellerRatingText: { fontSize: 14, fontWeight: 'bold', color: '#333' },
  sellerRatingCount: { fontSize: 13, color: '#999', marginRight: 2 },
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
  statusActions: { flex: 1, flexDirection: 'row', gap: 8 },
  statusButton: { flex: 1, height: 50, borderRadius: 8, justifyContent: 'center', alignItems: 'center' },
  statusButtonPrimary: { backgroundColor: '#00A859' },
  statusButtonOutline: { borderWidth: 1, borderColor: '#00A859', backgroundColor: '#FFF' },
  statusButtonOutlineText: { color: '#00A859', fontSize: 15, fontWeight: 'bold' },
  chatButtonText: { color: '#FFF', fontSize: 16, fontWeight: 'bold' },
});
