import React from 'react';
import { StyleSheet, View, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText'; // 경로가 다르면 맞게 수정해 주세요!

interface ShopBottomSheetProps {
  shop: any;
  categoryName: string;
  onClose: () => void;
  onPressDetail: (shopId: number) => void;
}

export default function ShopBottomSheet({ shop, categoryName, onClose, onPressDetail }: ShopBottomSheetProps) {
  return (
    <View style={styles.bottomSheet}>
      <TouchableOpacity style={styles.closeBtn} onPress={onClose}>
        <Ionicons name="close" size={24} color="#666" />
      </TouchableOpacity>

      <View style={styles.sheetContent}>
        <View style={styles.sheetInfo}>
          <Text style={styles.sheetCategory}>{categoryName}</Text>
          <Text fontWeight="bold" style={styles.sheetTitle}>{shop.name}</Text>
          
          <View style={styles.sheetRatingRow}>
            <Ionicons name="star" size={16} color="#FFD700" />
            <Text fontWeight="bold" style={styles.sheetRating}>{shop.rating || '0.0'}</Text>
            <Text style={styles.sheetReviewCount}> 리뷰 {shop.reviewCount || 0}</Text>
          </View>

          <View style={styles.sheetAddressRow}>
            <Ionicons name="location-outline" size={14} color="#888" />
            <Text style={styles.sheetAddress} numberOfLines={1}>{shop.address}</Text>
          </View>
        </View>
      </View>

      <TouchableOpacity 
        style={styles.sheetDetailBtn}
        onPress={() => onPressDetail(shop.storeId)}
      >
        <Text fontWeight="bold" style={styles.sheetDetailBtnText}>매장 상세 보기</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  bottomSheet: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    backgroundColor: '#fff', borderTopLeftRadius: 24, borderTopRightRadius: 24,
    padding: 24, paddingBottom: 40,
    shadowColor: '#000', shadowOffset: { width: 0, height: -4 }, shadowOpacity: 0.1, shadowRadius: 10, elevation: 20,
  },
  closeBtn: { position: 'absolute', top: 16, right: 16, padding: 8 },
  sheetContent: { flexDirection: 'row', marginBottom: 20 },
  sheetInfo: { flex: 1, justifyContent: 'center' },
  sheetCategory: { fontSize: 12, color: '#00A859', marginBottom: 4, fontWeight: 'bold' },
  sheetTitle: { fontSize: 20, color: '#333', marginBottom: 8 },
  sheetRatingRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  sheetRating: { fontSize: 15, color: '#333', marginLeft: 4 },
  sheetReviewCount: { fontSize: 13, color: '#888' },
  sheetAddressRow: { flexDirection: 'row', alignItems: 'center' },
  sheetAddress: { fontSize: 13, color: '#888', marginLeft: 4 },
  sheetDetailBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 12, alignItems: 'center' },
  sheetDetailBtnText: { color: '#fff', fontSize: 16 },
});