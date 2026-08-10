import React, { useState, useEffect } from 'react';
import { 
  View, StyleSheet, ScrollView, TouchableOpacity, 
  Dimensions, ActivityIndicator, Alert, Image 
} from 'react-native';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import { Text } from '../../components/CustomText';
import { shopApi } from '../../api/shop';
import { regionApi } from '../../api/region';
import { chatApi } from '../../api/chat';
import { cartApi } from '../../api/cart';

const { width } = Dimensions.get('window');

export default function ProductDetailScreen() {
  const router = useRouter();
  const { id, isRestaurant } = useLocalSearchParams();
  
  const insets = useSafeAreaInsets(); 

  const [isLoading, setIsLoading] = useState(true);
  const [productDetail, setProductDetail] = useState<any>(null);
  const [productOptions, setProductOptions] = useState<any[]>([]); // 💡 옵션 데이터 상태
  const [selectedOptions, setSelectedOptions] = useState<{ [groupName: string]: any }>({}); // 💡 유저가 선택한 옵션들
  
  const [quantity, setQuantity] = useState<number>(1);
  const [isVerified, setIsVerified] = useState<boolean>(false);

  const productIdNum = typeof id === 'string' ? Number(id) : 1;
  const isRestaurantProd = isRestaurant === 'true';

  useEffect(() => {
    const fetchProductData = async () => {
      try {
        setIsLoading(true);
        
        // 💡 상품 상세 정보, 동네 목록, 그리고 '상품 옵션'까지 병렬로 한 번에 조회합니다.
        const [productData, regionsRes, optionsRes] = await Promise.all([
          shopApi.getProductDetail(productIdNum),
          regionApi.getMyRegions().catch(() => null),
          shopApi.getProductOptions(productIdNum).catch(() => [])
        ]);
        
        console.log("🔥 실제 API 옵션 응답 데이터:", JSON.stringify(optionsRes, null, 2));

        setProductDetail(productData);
        setProductOptions(Array.isArray(optionsRes) ? optionsRes : (optionsRes?.data || []));

        // 💡 기본 선택 옵션 세팅 (기본값인 default: true 아이템이 있으면 미리 선택해 둡니다)
        if (Array.isArray(optionsRes)) {
          const initialSelected: { [key: string]: any } = {};
          optionsRes.forEach((group: any) => {
            if (group.items && Array.isArray(group.items)) {
              const defaultItem = group.items.find((item: any) => item.default === true) || group.items[0];
              if (defaultItem) {
                initialSelected[group.groupName] = defaultItem;
              }
            }
          });
          setSelectedOptions(initialSelected);
        }

        // 동네 인증 여부 검사
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

  // 💡 총 결제 금액 계산 (기본 가격 또는 할인가 + 선택한 옵션들의 추가 금액 * 수량)
  const calculateTotalPrice = () => {
    if (!productDetail) return 0;
    const basePrice = productDetail.hasEvent && productDetail.eventPrice ? productDetail.eventPrice : (productDetail.price || 0);
    
    let optionAdditionalPrice = 0;
    Object.values(selectedOptions).forEach((item: any) => {
      if (item && item.additionalPrice) {
        optionAdditionalPrice += item.additionalPrice;
      }
    });

    return (basePrice + optionAdditionalPrice) * quantity;
  };

  // 💡 옵션 선택 핸들러
  const handleSelectOption = (groupName: string, item: any) => {
    setSelectedOptions(prev => ({
      ...prev,
      [groupName]: item
    }));
  };

  // 장바구니 담기 / 주문하기 핸들러
  const handleAction = async () => {
    if (!isVerified) {
      Alert.alert(
        '동네 인증 필요', 
        '인증되지 않은 동네의 상품입니다.\n주문 및 장바구니 담기를 이용하시려면 동네 인증을 완료해주세요.'
      );
      return;
    }

    // 💡 필수 옵션 검사
    for (const group of productOptions) {
      if (group.required && !selectedOptions[group.groupName]) {
        Alert.alert('필수 옵션 선택', `"${group.groupName}" 옵션을 선택해주세요.`);
        return;
      }
    }

    if (isRestaurantProd) {
      Alert.alert('성공', '메뉴 선택이 완료되었습니다. 주문 화면으로 이동합니다.');
      router.push(`/order/${productIdNum}` as any);
    } else {
      try {
        // 선택한 옵션 아이디들 추출 (서버 API 규격에 맞게 변환 가능)
        const optionItemIds = Object.values(selectedOptions).map((item: any) => item.itemId);

        await cartApi.addCartItem({ 
          productId: productIdNum, 
          quantity: quantity,
          selectedOptionItemIds: optionItemIds 
        });
      
        Alert.alert('장바구니 담기 성공', `${productDetail?.name} ${quantity}개가 장바구니에 담겼습니다.`, [
          { text: '쇼핑 계속하기', style: 'cancel' },
          { text: '장바구니 보기', onPress: () => router.push('/cart') }
        ]);
      } catch (error) {
        console.error("장바구니 담기 오류:", error);
        Alert.alert('오류', '장바구니에 상품을 담는데 실패했습니다.');
      }
    }
  };

  // 단체 채팅 입장 로직
  const handleGroupChat = async () => {
    if (!isVerified) {
      Alert.alert('동네 인증 필요', '이 상점의 단체 채팅방에 참여하려면 마이페이지에서 대표 동네를 인증해주세요.');
      return;
    }

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
  const pPrice = productDetail.price || 0;
  const pEventPrice = productDetail.eventPrice || 0;
  const hasEvent = productDetail.hasEvent === true;
  const discountRate = hasEvent && pPrice > 0 && pEventPrice > 0 && pPrice > pEventPrice
    ? Math.round(((pPrice - pEventPrice) / pPrice) * 100)
    : 0;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 상단 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>상품 상세 정보</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 180 }}>
        {/* 상품 이미지 */}
        <Image source={{ uri: productImgUrl }} style={styles.productImg} />

        {/* 상품 정보 섹션 */}
        <View style={styles.infoSection}>
          <Text fontWeight="bold" style={styles.productName}>{productDetail.name}</Text>
          
          <View style={styles.priceRow}>
            {hasEvent && discountRate > 0 && (
              <Text style={styles.discountRateText}>{discountRate}%</Text>
            )}
            <Text fontWeight="bold" style={styles.productPrice}>
              {hasEvent && pEventPrice > 0 ? pEventPrice.toLocaleString() : pPrice.toLocaleString()}원
            </Text>
            {hasEvent && pEventPrice > 0 && pPrice > pEventPrice && (
              <Text style={styles.originalPriceText}>{pPrice.toLocaleString()}원</Text>
            )}
          </View>

          <View style={styles.divider} />
          <Text style={styles.productDescTitle}>상품 설명</Text>
          <Text style={styles.productDesc}>{productDetail.description || '등록된 상품 설명이 없습니다.'}</Text>
        </View>

        {/* 💡 상품 옵션 선택 섹션 (배달앱 스타일) */}
        {productOptions.length > 0 && (
          <View style={styles.optionSection}>
            <View style={styles.thickDivider} />
            
            {productOptions.map((group: any, index: number) => {
              // 💡 방어 로직: 백엔드 변수명이 items가 아닐 수 있으므로 여러 경우의 수 대비
              const items = group.items || group.optionItems || group.productOptionItems || [];
              const isSingle = group.selectionType === 'SINGLE' || group.selectionType !== 'MULTIPLE';

              return (
                <View key={`option-group-${group.optionId || index}`} style={styles.optionGroup}>
                  
                  {/* 옵션 그룹 헤더 */}
                  <View style={styles.optionGroupHeader}>
                    <View style={styles.optionTitleRow}>
                      <Text fontWeight="bold" style={styles.optionGroupName}>{group.groupName}</Text>
                      {group.required ? (
                        <View style={styles.requiredBadge}><Text style={styles.requiredBadgeText}>필수</Text></View>
                      ) : (
                        <View style={styles.optionalBadge}><Text style={styles.optionalBadgeText}>선택</Text></View>
                      )}
                    </View>
                    <Text style={styles.selectionHint}>
                      {isSingle ? '최대 1개 선택' : '여러 개 선택 가능'}
                    </Text>
                  </View>

                  {/* 세부 옵션 리스트 (세로형) */}
                  <View style={styles.optionItemsContainer}>
                    {items.length === 0 ? (
                      <Text style={styles.emptyOptionText}>선택할 수 있는 옵션이 없습니다.</Text>
                    ) : (
                      items.map((item: any, itemIndex: number) => {
                        // 선택 여부 확인
                        const isSelected = selectedOptions[group.groupName]?.itemId === item.itemId;
                        const isLastItem = itemIndex === items.length - 1;

                        return (
                          <TouchableOpacity
                            key={`option-item-${item.itemId || itemIndex}`}
                            style={[styles.optionItemRow, !isLastItem && styles.optionItemDivider]}
                            activeOpacity={0.7}
                            onPress={() => handleSelectOption(group.groupName, item)}
                          >
                            <View style={styles.optionItemLeft}>
                              {/* 단일 선택은 동그라미(라디오), 다중 선택은 네모(체크박스) 아이콘 */}
                              <Ionicons 
                                name={isSelected ? (isSingle ? "radio-button-on" : "checkbox") : (isSingle ? "radio-button-off" : "square-outline")} 
                                size={24} 
                                color={isSelected ? "#00A859" : "#CCC"} 
                                style={{ marginRight: 12 }}
                              />
                              <Text style={[styles.optionItemText, isSelected && styles.optionItemTextSelected]}>
                                {item.itemName || item.item_name}
                              </Text>
                            </View>
                            
                            <Text style={styles.optionPriceText}>
                              {item.additionalPrice > 0 ? `+${item.additionalPrice.toLocaleString()}원` : ''}
                            </Text>
                          </TouchableOpacity>
                        );
                      })
                    )}
                  </View>
                  
                  {/* 옵션 그룹 간 구분선 */}
                  <View style={styles.thickDivider} />
                </View>
              );
            })}
          </View>
        )}

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

      {/* 하단 구매 / 채팅 버튼 바 */}
      <View style={[styles.bottomBar, { paddingBottom: Math.max(insets.bottom, 15) + 10 }]}>
        {/* 💡 총 금액 표시 바 */}
        <View style={styles.totalPriceRow}>
          <Text style={styles.totalLabel}>총 금액</Text>
          <Text fontWeight="bold" style={styles.totalPriceValue}>{calculateTotalPrice().toLocaleString()}원</Text>
        </View>

        <TouchableOpacity style={styles.primaryBtn} onPress={handleAction}>
          <Text fontWeight="bold" style={styles.primaryBtnText}>
            {isRestaurantProd ? '메뉴 선택하기' : '장바구니 담기'}
          </Text>
        </TouchableOpacity>

        <View style={styles.rowButtons}>
          <TouchableOpacity 
            style={styles.halfButton} 
            activeOpacity={0.7}
            onPress={() => {
              const targetStoreId = productDetail?.storeId || productDetail?.shopId;
              if (!targetStoreId) {
                Alert.alert('알림', '상점 정보를 찾을 수 없습니다.');
                return;
              }
              router.push(`/inquiry/write?storeId=${targetStoreId}` as any);
            }}
          >
            <Ionicons name="chatbubble-outline" size={18} color="#00A859" style={{ marginRight: 6 }} />
            <Text style={styles.halfButtonText}>상품 문의</Text>
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
  priceRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  discountRateText: { fontSize: 20, fontWeight: 'bold', color: '#FF5252', marginRight: 8 },
  productPrice: { fontSize: 20, color: '#00A859' },
  originalPriceText: { fontSize: 15, color: '#999', textDecorationLine: 'line-through', marginLeft: 8 },
  divider: { height: 1, backgroundColor: '#F0F0F0', marginVertical: 15 },
  productDescTitle: { fontSize: 15, fontWeight: 'bold', color: '#333', marginBottom: 8 },
  productDesc: { fontSize: 14, color: '#666', lineHeight: 22 },

  // 옵션 선택 섹션 스타일
  optionSection: { marginBottom: 20 },
  thickDivider: { height: 8, backgroundColor: '#F2F4F7' },
  optionGroup: { backgroundColor: '#FFF' },
  optionGroupHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 18 },
  optionTitleRow: { flexDirection: 'row', alignItems: 'center' },
  optionGroupName: { fontSize: 18, color: '#333' },
  requiredBadge: { backgroundColor: '#FFEDEE', paddingHorizontal: 6, paddingVertical: 3, borderRadius: 4, marginLeft: 8 },
  requiredBadgeText: { fontSize: 12, color: '#FF5252', fontWeight: 'bold' },
  optionalBadge: { backgroundColor: '#F0F0F0', paddingHorizontal: 6, paddingVertical: 3, borderRadius: 4, marginLeft: 8 },
  optionalBadgeText: { fontSize: 12, color: '#666' },
  selectionHint: { fontSize: 13, color: '#888' },
  
  optionItemsContainer: { paddingHorizontal: 20 },
  emptyOptionText: { fontSize: 14, color: '#999', paddingBottom: 20 },
  optionItemRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 16 },
  optionItemDivider: { borderBottomWidth: 1, borderBottomColor: '#F5F5F5' },
  optionItemLeft: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  optionItemText: { fontSize: 16, color: '#333' },
  optionItemTextSelected: { fontWeight: 'bold', color: '#333' },
  optionPriceText: { fontSize: 15, color: '#333' },

  quantitySection: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15, borderTopWidth: 1, borderBottomWidth: 1, borderColor: '#F5F5F5', marginBottom: 20 },
  quantityLabel: { fontSize: 16, color: '#333' },
  quantityController: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, overflow: 'hidden' },
  qtyBtn: { backgroundColor: '#F5F5F5', padding: 10, justifyContent: 'center', alignItems: 'center' },
  qtyText: { paddingHorizontal: 15, fontSize: 15, color: '#333' },
  
  bottomBar: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff', position: 'absolute', bottom: 0, width: '100%' },
  totalPriceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, paddingHorizontal: 4 },
  totalLabel: { fontSize: 14, color: '#666' },
  totalPriceValue: { fontSize: 18, color: '#00A859' },

  primaryBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center', justifyContent: 'center', marginBottom: 10 },
  primaryBtnText: { color: '#fff', fontSize: 16 },
  
  rowButtons: { flexDirection: 'row', justifyContent: 'space-between' },
  halfButton: { flex: 1, flexDirection: 'row', backgroundColor: '#FFF', borderWidth: 1, borderColor: '#00A859', paddingVertical: 12, borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginHorizontal: 4 },
  halfValueText: { color: '#00A859', fontSize: 14 },
  halfButtonText: { color: '#00A859', fontSize: 14 },
});