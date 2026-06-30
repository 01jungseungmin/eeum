import React, { useState, useEffect } from 'react';
import { 
  View, StyleSheet, Image, ScrollView, TouchableOpacity, 
  Dimensions, ActivityIndicator, Alert 
} from 'react-native';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import { Text } from '../../components/CustomText';
import { shopApi } from '../../api/shop';
import { regionApi } from '../../api/region';
import { chatApi } from '../../api/chat';

const { width } = Dimensions.get('window');

export default function ProductDetailScreen() {
  const router = useRouter();
  const { id, isRestaurant } = useLocalSearchParams();
  
  const insets = useSafeAreaInsets(); 

  const [isLoading, setIsLoading] = useState(true);
  const [productDetail, setProductDetail] = useState<any>(null);
  const [quantity, setQuantity] = useState<number>(1);
  
  const [isVerified, setIsVerified] = useState<boolean>(false);

  const productIdNum = typeof id === 'string' ? Number(id) : 1;
  const isRestaurantProd = isRestaurant === 'true';

  useEffect(() => {
    const fetchProductData = async () => {
      try {
        setIsLoading(true);
        
        // 상품 상세 정보와 유저의 동네 목록을 동시에 조회합니다.
        const [productData, regionsRes] = await Promise.all([
          shopApi.getProductDetail?.(productIdNum) || shopApi.getShopProducts(1).then(res => res[0]), // 예시 방어코드
          regionApi.getMyRegions().catch(() => null)
        ]);

        setProductDetail(productData);

        // 상품(또는 해당 상점)의 regionId가 유저의 인증된 동네 목록에 있는지 검사
        if (regionsRes?.data && productData) {
          const primaryRegion = regionsRes.data.find((r: any) => r.isPrimary === true);
          const isPrimaryVerified = primaryRegion?.verified === true || primaryRegion?.isVerified === true;
          
          const targetRegionId = productData.regionId || productData.shop?.regionId;
          const isStoreRegionVerified = regionsRes.data.some(
            (r: any) => r.regionId === targetRegionId && (r.verified === true || r.isVerified === true)
          );
          
          setIsVerified(isPrimaryVerified || isStoreRegionVerified);
        }

      } catch (e) {
        console.error("상품 데이터 로딩 실패:", e);
        Alert.alert("오류", "상품 정보를 불러오지 못했습니다.");
        router.back();
      } finally {
        setIsLoading(false);
      }
    };

    if (productIdNum) fetchProductData();
  }, [productIdNum]);

  // 장바구니 담기 / 주문하기 핸들러 (방어벽 작동)
  const handleAction = () => {
    if (!isVerified) {
      Alert.alert(
        '동네 인증 필요', 
        '인증되지 않은 동네의 상품입니다.\n주문 및 장바구니 담기를 이용하시려면 동네 인증을 완료해주세요.'
      );
      return;
    }

    if (isRestaurantProd) {
      Alert.alert('성공', '메뉴 선택이 완료되었습니다. 주문 화면으로 이동합니다.');
      // router.push('/order'); // 추후 주문 페이지 구현 시 활성화
    } else {
      Alert.alert('장바구니 담기 성공', `${productDetail?.name} ${quantity}개가 장바구니에 담겼습니다.`, [
        { text: '쇼핑 계속하기', style: 'cancel' },
        { text: '장바구니 보기', onPress: () => router.push('/cart') }
      ]);
    }
  };

  // 단체 채팅 입장 로직 (상점 상세 화면과 동일)
  const handleGroupChat = async () => {
    if (!isVerified) {
      Alert.alert('동네 인증 필요', '이 상점의 단체 채팅방에 참여하려면 마이페이지에서 대표 동네를 인증해주세요.');
      return;
    }

    // 상품 정보에 포함된 상점의 채팅방 ID를 찾습니다 (백엔드 응답 구조에 맞게 필드명 확인 필요)
    const roomId = productDetail?.chatRoomId || productDetail?.groupChatRoomId || productDetail?.shop?.chatRoomId; 

    if (!roomId) {
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
      <View style={styles.centerLoading}>
        <ActivityIndicator size="large" color="#00A859" />
      </View>
    );
  }

  if (!productDetail) return null;

  const productImgUrl = productDetail.imageUrl || productDetail.thumbnailUrl || 'https://via.placeholder.com/600x600/E8F5E9/00A859?text=Product';

  return (
    // edges=['top'] 으로 설정하여 하단 영역 패딩을 직접 제어합니다.
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 상단 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>상품 상세 정보</Text>
        <View style={{ width: 24 }} />
      </View>

      {/* 하단 바가 높아졌으므로 ScrollView의 paddingBottom을 넉넉하게 160으로 늘려줍니다. */}
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 160 }}>
        {/* 상품 이미지 */}
        <Image source={{ uri: productImgUrl }} style={styles.productImg} />

        {/* 상품 정보 섹션 */}
        <View style={styles.infoSection}>
          <Text fontWeight="bold" style={styles.productName}>{productDetail.name}</Text>
          <Text fontWeight="bold" style={styles.productPrice}>
            {productDetail.price?.toLocaleString()}원
          </Text>
          <View style={styles.divider} />
          <Text style={styles.productDescTitle}>상품 설명</Text>
          <Text style={styles.productDesc}>{productDetail.description || '등록된 상품 설명이 없습니다.'}</Text>
        </View>

        {/* 수량 선택 섹션 (식당 메뉴가 아닐 때만 노출) */}
        {!isRestaurantProd && (
          <View style={styles.quantitySection}>
            <Text fontWeight="bold" style={styles.quantityLabel}>수량</Text>
            <View style={styles.quantityController}>
              <TouchableOpacity 
                style={styles.qtyBtn} 
                onPress={() => setQuantity(prev => Math.max(1, prev - 1))}
              >
                <Ionicons name="remove" size={18} color="#333" />
              </TouchableOpacity>
              <Text fontWeight="bold" style={styles.qtyText}>{quantity}</Text>
              <TouchableOpacity 
                style={styles.qtyBtn} 
                onPress={() => setQuantity(prev => prev + 1)}
              >
                <Ionicons name="add" size={18} color="#333" />
              </TouchableOpacity>
            </View>
          </View>
        )}
      </ScrollView>

      {/* 하단 구매 / 채팅 버튼 바 (안전 영역 패딩 적용) */}
      <View style={[styles.bottomBar, { paddingBottom: Math.max(insets.bottom, 15) + 10 }]}>
        {/* 1. 메인 액션 버튼 (장바구니 / 메뉴 선택) */}
        <TouchableOpacity style={styles.primaryBtn} onPress={handleAction}>
          <Text fontWeight="bold" style={styles.primaryBtnText}>
            {isRestaurantProd ? '메뉴 선택하기' : '장바구니 담기'}
          </Text>
        </TouchableOpacity>

        {/* 2. 하단 2분할 버튼 (문의하기 & 단체 채팅) */}
        <View style={styles.rowButtons}>
          <TouchableOpacity 
            style={styles.halfButton} 
            activeOpacity={0.7}
            onPress={() => Alert.alert('안내', '문의하기 기능은 준비 중입니다.')}
          >
            <Ionicons name="chatbubble-outline" size={18} color="#00A859" style={{ marginRight: 6 }} />
            <Text style={styles.halfButtonText}>문의하기</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.halfButton} 
            activeOpacity={0.7} 
            onPress={handleGroupChat}
          >
            <Ionicons name="chatbubbles-outline" size={18} color="#00A859" style={{ marginRight: 6 }} />
            <Text style={styles.halfButtonText}>단체 채팅</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  centerLoading: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  backBtn: { padding: 4 },
  headerTitle: { fontSize: 18, color: '#333' },
  productImg: { width: width, height: width, backgroundColor: '#F9F9F9' },
  infoSection: { padding: 20 },
  productName: { fontSize: 22, color: '#333', marginBottom: 8 },
  productPrice: { fontSize: 20, color: '#00A859', marginBottom: 15 },
  divider: { height: 1, backgroundColor: '#F0F0F0', marginVertical: 15 },
  productDescTitle: { fontSize: 15, fontWeight: 'bold', color: '#333', marginBottom: 8 },
  productDesc: { fontSize: 14, color: '#666', lineHeight: 22 },
  quantitySection: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15, borderTopWidth: 1, borderBottomWidth: 1, borderColor: '#F5F5F5', marginBottom: 20 },
  quantityLabel: { fontSize: 16, color: '#333' },
  quantityController: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, overflow: 'hidden' },
  qtyBtn: { backgroundColor: '#F5F5F5', padding: 10, justifyContent: 'center', alignItems: 'center' },
  qtyText: { paddingHorizontal: 15, fontSize: 15, color: '#333' },
  
  // ✨ 바텀 바 및 버튼 스타일 업데이트
  bottomBar: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  primaryBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center', justifyContent: 'center', marginBottom: 10 },
  primaryBtnText: { color: '#fff', fontSize: 16 },
  
  rowButtons: { flexDirection: 'row', justifyContent: 'space-between' },
  halfButton: { flex: 1, flexDirection: 'row', backgroundColor: '#FFF', borderWidth: 1, borderColor: '#00A859', paddingVertical: 12, borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginHorizontal: 4 },
  halfButtonText: { color: '#00A859', fontSize: 14 },
});