import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  Image,
  ScrollView,
  TouchableOpacity,
  Dimensions,
} from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

const { width } = Dimensions.get('window');

// 🛠️ 임시 가짜 데이터
const MOCK_SHOP_DETAIL = {
  id: 's1',
  name: '초록마트 잠실점',
  category: '동네마트',
  location: '송파구 잠실본동 123-45',
  rating: 4.8,
  reviewCount: 128,
  description:
    '매일 아침 가락시장에서 직접 경매받아 온 신선한 야채와 과일을 판매합니다. 동네 주민분들을 위해 항상 최저가로 모시겠습니다!',
  coverImg: 'https://via.placeholder.com/600x300/386641/FFFFFF?text=Shop+Cover',
  profileImg: 'https://via.placeholder.com/100/E8F5E9/00A859?text=M',
};

const SHOP_PRODUCTS = [
  {
    id: 'p1',
    name: '유기농 사과 1박스',
    price: 12000,
    img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Apple',
  },
  {
    id: 'p2',
    name: '무항생제 특란 30구',
    price: 6500,
    img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Egg',
  },
  {
    id: 'p3',
    name: '제주 감귤 3kg',
    price: 9900,
    img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Orange',
  },
  {
    id: 'p4',
    name: '국산 양파 1.5kg',
    price: 3500,
    img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Onion',
  },
];

const SHOP_REVIEWS = [
  {
    id: 'r1',
    user: '당근러버',
    rating: 5,
    date: '2026.05.01',
    content: '과일이 정말 신선하고 사장님이 친절하세요!',
  },
  {
    id: 'r2',
    user: '잠실토박이',
    rating: 4,
    date: '2026.04.28',
    content: '계란 저렴하게 잘 샀습니다. 배달도 빠르네요.',
  },
];

export default function ShopDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();

  // 상품 목록 / 리뷰 탭 전환을 위한 상태
  const [activeTab, setActiveTab] = useState<'products' | 'reviews'>(
    'products',
  );
  const shop = MOCK_SHOP_DETAIL;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 1. 커버 이미지 & 뒤로가기 */}
        <View style={styles.coverContainer}>
          <Image source={{ uri: shop.coverImg }} style={styles.coverImage} />
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => router.back()}
          >
            <Ionicons name="chevron-back" size={28} color="#fff" />
          </TouchableOpacity>
        </View>

        {/* 2. 상점 프로필 정보 */}
        <View style={styles.infoContainer}>
          <View style={styles.profileHeader}>
            <Image
              source={{ uri: shop.profileImg }}
              style={styles.profileImage}
            />
            <View style={styles.titleArea}>
              <Text style={styles.shopName}>{shop.name}</Text>
              <Text style={styles.shopCategory}>{shop.category}</Text>
            </View>
          </View>

          <Text style={styles.shopDescription}>{shop.description}</Text>

          <View style={styles.metaInfo}>
            <Ionicons name="location-outline" size={16} color="#888" />
            <Text style={styles.metaText}>{shop.location}</Text>
          </View>
          <View style={styles.metaInfo}>
            <Ionicons name="star" size={16} color="#FFD700" />
            <Text style={styles.metaText}>
              {shop.rating} ({shop.reviewCount}개의 리뷰)
            </Text>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 3. 탭 버튼 (판매상품 / 리뷰) */}
        <View style={styles.tabContainer}>
          <TouchableOpacity
            style={[
              styles.tabButton,
              activeTab === 'products' && styles.tabButtonActive,
            ]}
            onPress={() => setActiveTab('products')}
          >
            <Text
              style={[
                styles.tabText,
                activeTab === 'products' && styles.tabTextActive,
              ]}
            >
              판매상품
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[
              styles.tabButton,
              activeTab === 'reviews' && styles.tabButtonActive,
            ]}
            onPress={() => setActiveTab('reviews')}
          >
            <Text
              style={[
                styles.tabText,
                activeTab === 'reviews' && styles.tabTextActive,
              ]}
            >
              리뷰 {shop.reviewCount}
            </Text>
          </TouchableOpacity>
        </View>

        {/* 4. 탭 내용 렌더링 */}
        <View style={styles.contentContainer}>
          {activeTab === 'products' ? (
            // 판매상품 리스트 (2열 그리드)
            <View style={styles.productGrid}>
              {SHOP_PRODUCTS.map((product) => (
                <TouchableOpacity
                  key={product.id}
                  style={styles.productCard}
                  onPress={() =>
                    router.push({
                      pathname: '/product/[id]',
                      params: { id: product.id },
                    })
                  }
                >
                  <Image
                    source={{ uri: product.img }}
                    style={styles.productImage}
                  />
                  <Text style={styles.productName} numberOfLines={1}>
                    {product.name}
                  </Text>
                  <Text style={styles.productPrice}>
                    {product.price.toLocaleString()}원
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          ) : (
            // 리뷰 리스트
            <View style={styles.reviewList}>
              {SHOP_REVIEWS.map((review) => (
                <View key={review.id} style={styles.reviewCard}>
                  <View style={styles.reviewHeader}>
                    <Text style={styles.reviewUser}>{review.user}</Text>
                    <View style={styles.ratingStars}>
                      <Ionicons name="star" size={14} color="#FFD700" />
                      <Text style={styles.reviewRating}>{review.rating}</Text>
                    </View>
                  </View>
                  <Text style={styles.reviewContent}>{review.content}</Text>
                  <Text style={styles.reviewDate}>{review.date}</Text>
                </View>
              ))}
            </View>
          )}
        </View>
      </ScrollView>

      {/* 5. 하단 액션 바 */}
      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.likeButton}>
          <Ionicons name="heart-outline" size={24} color="#888" />
          <Text style={{ fontSize: 12, color: '#888', marginTop: 2 }}>
            단골등록
          </Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.chatButton}>
          <Text style={styles.chatButtonText}>사장님께 문의하기</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  coverContainer: { position: 'relative', width: '100%', height: 200 },
  coverImage: { width: '100%', height: '100%', resizeMode: 'cover' },
  backButton: {
    position: 'absolute',
    top: 15,
    left: 15,
    backgroundColor: 'rgba(0,0,0,0.3)',
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  infoContainer: {
    padding: 20,
    marginTop: -30,
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
  },
  profileHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 15,
  },
  profileImage: {
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 3,
    borderColor: '#fff',
    backgroundColor: '#eee',
  },
  titleArea: { marginLeft: 15 },
  shopName: { fontSize: 22, fontWeight: 'bold', color: '#333' },
  shopCategory: { fontSize: 14, color: '#00A859', marginTop: 4 },
  shopDescription: {
    fontSize: 15,
    color: '#444',
    lineHeight: 22,
    marginBottom: 20,
  },
  metaInfo: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  metaText: { fontSize: 14, color: '#666', marginLeft: 8 },
  divider: { height: 8, backgroundColor: '#F5F5F5' },
  tabContainer: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  tabButton: { flex: 1, paddingVertical: 15, alignItems: 'center' },
  tabButtonActive: { borderBottomWidth: 2, borderBottomColor: '#00A859' },
  tabText: { fontSize: 16, color: '#888', fontWeight: '500' },
  tabTextActive: { color: '#00A859', fontWeight: 'bold' },
  contentContainer: { padding: 20, paddingBottom: 40 },
  productGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  productCard: { width: (width - 55) / 2, marginBottom: 20 },
  productImage: {
    width: '100%',
    height: (width - 55) / 2,
    borderRadius: 8,
    marginBottom: 8,
  },
  productName: { fontSize: 15, color: '#333', marginBottom: 4 },
  productPrice: { fontSize: 16, fontWeight: 'bold', color: '#333' },
  reviewList: { width: '100%' },
  reviewCard: {
    marginBottom: 20,
    paddingBottom: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F0F0',
  },
  reviewHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  reviewUser: { fontSize: 15, fontWeight: 'bold', color: '#333' },
  ratingStars: { flexDirection: 'row', alignItems: 'center' },
  reviewRating: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#FFD700',
    marginLeft: 4,
  },
  reviewContent: {
    fontSize: 15,
    color: '#444',
    lineHeight: 22,
    marginBottom: 8,
  },
  reviewDate: { fontSize: 12, color: '#888' },
  bottomBar: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    paddingVertical: 15,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
    backgroundColor: '#fff',
  },
  likeButton: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingRight: 20,
  },
  chatButton: {
    flex: 1,
    backgroundColor: '#00A859',
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 12,
  },
  chatButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
});
