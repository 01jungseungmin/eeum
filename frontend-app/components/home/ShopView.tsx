import React from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

// 더미 데이터 내부 포함
const SHOP_LIST = [
  { id: 's1', name: '라떼가 맛있는 집', category: '카페', img: 'https://via.placeholder.com/150/333333/FFFFFF?text=Cafe' },
  { id: 's2', name: '소문난 한식당', category: '식당', img: 'https://via.placeholder.com/150/555555/FFFFFF?text=Korean' },
  { id: 's3', name: '매일 굽는 베이커리', category: '베이커리', img: 'https://via.placeholder.com/150/777777/FFFFFF?text=Bakery' },
];

export default function ShopView({ router }: { router: any }) {
  return (
    <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 30 }}>
      <View style={styles.bannerPlaceholder}>
        <Text style={{ color: '#fff' }}>이벤트 배너 영역</Text>
      </View>
      <View style={styles.sectionContainer}>
        <TouchableOpacity 
          style={styles.sectionHeader} 
          onPress={() => router.push('/shop/list')}
        >
          <Text style={styles.sectionTitle}>우리 동네 상점</Text>
          <Ionicons name="chevron-forward" size={20} color="#333" />
        </TouchableOpacity>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          {SHOP_LIST.map((shop) => (
            <TouchableOpacity 
              key={shop.id} 
              style={styles.shopCard} 
              onPress={() => router.push({ pathname: '/shop/[id]', params: { id: shop.id } })}
            >
              <Image source={{ uri: shop.img }} style={styles.shopImage} />
              <Text style={styles.shopName}>{shop.name}</Text>
              <Text style={styles.shopCategory}>{shop.category}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  bannerPlaceholder: { height: 180, backgroundColor: '#386641', justifyContent: 'center', alignItems: 'center', marginHorizontal: 20, borderRadius: 8, marginBottom: 25 },
  sectionContainer: { paddingLeft: 20, marginBottom: 30 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', paddingRight: 20, alignItems: 'center', marginBottom: 15 },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  shopCard: { marginRight: 15, width: 120 },
  shopImage: { width: 120, height: 120, borderRadius: 8, marginBottom: 8 },
  shopName: { fontSize: 15, fontWeight: '600', color: '#333', marginBottom: 2 },
  shopCategory: { fontSize: 12, color: '#888' },
});