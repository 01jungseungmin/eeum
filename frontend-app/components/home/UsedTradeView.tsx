import React from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, FlatList, Image, Dimensions } from 'react-native';
import { Text } from '../CustomText';

const { width } = Dimensions.get('window');
const USED_CATEGORIES = ['전체', '농산물', '의류', '잡화', '가전'];
const USED_PRODUCTS = [
  { id: 'u1', title: '잔치국수 냄비', location: '송파동', price: 5000, img: 'https://via.placeholder.com/150/E8F5E9/00A859?text=Pot' },
  { id: 'u2', title: '유기농 사과 한 박스', location: '군자동', price: 12000, img: 'https://via.placeholder.com/150/FFF3E0/FF9800?text=Apple' },
];

interface UsedTradeViewProps {
  router: any;
  selectedCategory: string;
  setSelectedCategory: (cat: string) => void;
}

export default function UsedTradeView({ router, selectedCategory, setSelectedCategory }: UsedTradeViewProps) {
  return (
    <View style={{ flex: 1 }}>
      <View style={styles.categoryContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 15 }}>
          {USED_CATEGORIES.map((cat) => (
            <TouchableOpacity 
              key={cat} 
              style={[styles.categoryPill, selectedCategory === cat && styles.categoryPillActive]} 
              onPress={() => setSelectedCategory(cat)}
            >
              <Text style={[styles.categoryPillText, selectedCategory === cat && styles.categoryPillTextActive]}>{cat}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>
      <FlatList
        data={USED_PRODUCTS}
        keyExtractor={(item) => item.id}
        numColumns={2}
        columnWrapperStyle={{ justifyContent: 'space-between', paddingHorizontal: 15 }}
        showsVerticalScrollIndicator={false}
        renderItem={({ item }) => (
          <TouchableOpacity 
            style={styles.usedProductCard} 
            onPress={() => router.push({ pathname: '/product/[id]', params: { id: item.id } })}
          >
            <Image source={{ uri: item.img }} style={styles.usedProductImage} />
            <Text style={styles.usedProductTitle} numberOfLines={1}>{item.title}</Text>
            <Text style={styles.usedProductPrice}>{item.price.toLocaleString()}원</Text>
          </TouchableOpacity>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  categoryContainer: { paddingVertical: 10 },
  categoryPill: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: '#F5F5F5', marginRight: 8 },
  categoryPillActive: { backgroundColor: '#00A859' },
  categoryPillText: { fontSize: 14, color: '#666' },
  categoryPillTextActive: { color: '#fff', fontWeight: 'bold' },
  usedProductCard: { width: (width - 45) / 2, marginBottom: 20 },
  usedProductImage: { width: '100%', height: (width - 45) / 2, borderRadius: 8, marginBottom: 10 },
  usedProductTitle: { fontSize: 15, fontWeight: '500', color: '#333', marginBottom: 4 },
  usedProductPrice: { fontSize: 15, fontWeight: 'bold', color: '#333' },
});