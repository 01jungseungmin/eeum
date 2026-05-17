import React, { useState } from 'react';
import { 
  View, 
  StyleSheet, 
  FlatList, 
  Image, 
  TouchableOpacity, 
  TextInput,
  ScrollView 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';

// 카테고리 더미 데이터
const CATEGORIES = ['전체', '행사중', '식당', '카페', '마트'];

// 상점 더미 데이터 (피그마 이미지 기반)
const SHOP_LIST = [
  { id: '1', name: '곱도리 식당', rating: 4.9, reviews: 342, location: '등촌제2동', likes: 45, visitors: 89, isLiked: false, img: 'https://via.placeholder.com/300/E8F5E9/00A859?text=Food1' },
  { id: '2', name: '베이커리 밀크빵', rating: 4.9, reviews: 342, location: '등촌제1동', likes: 56, visitors: 123, isLiked: true, img: 'https://via.placeholder.com/300/FFF3E0/FF9800?text=Bread' },
  { id: '3', name: '동대문엽기떡볶이', rating: 4.8, reviews: 210, location: '등촌제1동', likes: 88, visitors: 150, isLiked: false, img: 'https://via.placeholder.com/300/FFEBEE/F44336?text=Spicy' },
  { id: '4', name: '전통 방앗간', rating: 4.7, reviews: 95, location: '등촌제2동', likes: 30, visitors: 60, isLiked: false, img: 'https://via.placeholder.com/300/EFEBE9/795548?text=Traditional' },
];

export default function ShopListScreen() {
  const router = useRouter();
  const [selectedCategory, setSelectedCategory] = useState('전체');

  // 🧩 개별 상점 카드 컴포넌트
  const renderShopCard = ({ item }: any) => (
    <TouchableOpacity 
      style={styles.cardContainer}
      onPress={() => router.push(`/shop/${item.id}`)}
    >
      <Image source={{ uri: item.img }} style={styles.cardImage} />
      
      <View style={styles.cardTitleRow}>
        <Text fontWeight="bold" style={styles.shopName} numberOfLines={1}>{item.name}</Text>
        <Ionicons 
          name={item.isLiked ? "heart" : "heart-outline"} 
          size={20} 
          color={item.isLiked ? "#FF5252" : "#999"} 
        />
      </View>

      <View style={styles.ratingRow}>
        <Ionicons name="star" size={14} color="#FFD700" />
        <Text fontWeight="bold" style={styles.ratingText}>{item.rating}</Text>
        <Text style={styles.reviewText}>({item.reviews})</Text>
      </View>

      <Text style={styles.locationText}>{item.location}</Text>

      <View style={styles.footerRow}>
        <View style={styles.footerItem}>
          <Ionicons name="heart" size={12} color="#999" />
          <Text style={styles.footerText}>{item.likes}</Text>
        </View>
        <View style={styles.footerItem}>
          <Ionicons name="people" size={14} color="#999" />
          <Text style={styles.footerText}>{item.visitors}</Text>
        </View>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 1. 상단 위치 및 헤더 (뒤로가기 포함) */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 10 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Ionicons name="location-outline" size={20} color="#333" />
        <Text fontWeight="bold" style={styles.headerLocation}>현재 이어진 곳은 등촌제1동</Text>
      </View>

      {/* 2. 검색창 */}
      <View style={styles.searchContainer}>
        <TextInput 
          style={styles.searchInput} 
          placeholder="검색어를 입력해주세요" 
          placeholderTextColor="#999"
        />
        <Ionicons name="search" size={20} color="#333" style={styles.searchIcon} />
      </View>

      {/* 3. 카테고리 필터 */}
      <View style={styles.categoryWrapper}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          {CATEGORIES.map((cat) => (
            <TouchableOpacity 
              key={cat} 
              style={[styles.categoryPill, selectedCategory === cat && styles.categoryPillActive]}
              onPress={() => setSelectedCategory(cat)}
            >
              <Text style={[styles.categoryText, selectedCategory === cat && styles.categoryTextActive]}>
                {cat}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* 4. 리스트 헤더 (총 개수 및 정렬) */}
      <View style={styles.listHeader}>
        <Text style={styles.totalText}>총 {SHOP_LIST.length}개</Text>
        <TouchableOpacity style={styles.sortButton}>
          <Text style={styles.sortText}>최신순</Text>
          <Ionicons name="chevron-down" size={14} color="#666" />
        </TouchableOpacity>
      </View>

      {/* 5. 2단 그리드 리스트 */}
      <FlatList
        data={SHOP_LIST}
        keyExtractor={(item) => item.id}
        renderItem={renderShopCard}
        numColumns={2}
        columnWrapperStyle={styles.rowWrapper}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 30 }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 10 },
  headerLocation: { fontSize: 16, color: '#333', marginLeft: 5 },
  
  searchContainer: { marginHorizontal: 20, marginVertical: 10, position: 'relative', justifyContent: 'center' },
  searchInput: { backgroundColor: '#F5F5F5', borderRadius: 8, paddingVertical: 12, paddingHorizontal: 15, fontSize: 14 },
  searchIcon: { position: 'absolute', right: 15 },

  categoryWrapper: { paddingLeft: 20, marginBottom: 15 },
  categoryPill: { backgroundColor: '#F5F5F5', paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, marginRight: 8 },
  categoryPillActive: { backgroundColor: '#00A859' },
  categoryText: { color: '#666', fontSize: 13 },
  categoryTextActive: { color: '#fff', fontWeight: 'bold' },

  listHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, marginBottom: 15 },
  totalText: { fontSize: 14, color: '#333', fontWeight: 'bold' },
  sortButton: { flexDirection: 'row', alignItems: 'center' },
  sortText: { fontSize: 13, color: '#666', marginRight: 4 },

  rowWrapper: { justifyContent: 'space-between', paddingHorizontal: 20 },
  cardContainer: { width: '48%', marginBottom: 25 },
  cardImage: { width: '100%', aspectRatio: 1, borderRadius: 12, marginBottom: 10 },
  
  cardTitleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  shopName: { fontSize: 15, color: '#333', flex: 1, marginRight: 5 },
  
  ratingRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  ratingText: { fontSize: 13, color: '#333', marginLeft: 4, marginRight: 4 },
  reviewText: { fontSize: 12, color: '#888' },
  
  locationText: { fontSize: 12, color: '#888', marginBottom: 6 },
  
  footerRow: { flexDirection: 'row', alignItems: 'center' },
  footerItem: { flexDirection: 'row', alignItems: 'center', marginRight: 10 },
  footerText: { fontSize: 11, color: '#999', marginLeft: 4 },
});