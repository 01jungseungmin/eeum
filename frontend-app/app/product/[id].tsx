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

// 🛠️ 임시 가짜 데이터 (나중에는 전달받은 id값으로 서버에서 불러옵니다)
const MOCK_PRODUCT_DETAIL = {
  id: '1',
  title: '신선한 유기농 엑스트라버진 올리브 오일 500ml',
  category: '농산물 · 2시간 전',
  price: 15000,
  description: `스페인산 최고급 유기농 올리브 오일입니다.\n샐러드 드레싱이나 가벼운 볶음 요리에 아주 좋습니다.\n유통기한은 넉넉하게 내년 12월까지입니다.\n\n* 동네 상점 특가로 한정 수량 5개만 판매합니다!`,
  likes: 12,
  views: 145,
  img: 'https://via.placeholder.com/400/E8F5E9/00A859?text=Olive+Oil',
  seller: {
    name: '초록마트',
    location: '송파구 잠실본동',
    rating: 4.8,
    profileImg: 'https://via.placeholder.com/50/333333/FFFFFF?text=Mart',
  },
};

export default function ProductDetailScreen() {
  const router = useRouter();
  // ✨ URL에서 넘어온 상품 id값을 꺼냅니다! (예: /product/1 이면 id는 '1')
  const { id } = useLocalSearchParams();

  const [isLiked, setIsLiked] = useState(false);
  const product = MOCK_PRODUCT_DETAIL; // 나중에는 id로 데이터를 찾아오는 로직이 들어갑니다.

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        style={styles.scrollArea}
      >
        {/* 1. 상품 이미지 & 뒤로가기 버튼 */}
        <View style={styles.imageContainer}>
          <Image source={{ uri: product.img }} style={styles.productImage} />
          {/* 플로팅 뒤로가기 버튼 */}
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => router.back()}
          >
            <Ionicons name="chevron-back" size={28} color="#333" />
          </TouchableOpacity>
        </View>

        {/* 2. 판매자(상점) 정보 프로필 영역 */}
        <View style={styles.profileSection}>
          <Image
            source={{ uri: product.seller.profileImg }}
            style={styles.profileImage}
          />
          <View style={styles.profileInfo}>
            <Text style={styles.profileName}>{product.seller.name}</Text>
            <Text style={styles.profileLocation}>
              {product.seller.location}
            </Text>
          </View>
          <View style={styles.ratingBadge}>
            <Ionicons name="star" size={14} color="#FFD700" />
            <Text style={styles.ratingText}>{product.seller.rating}</Text>
          </View>
        </View>

        <View style={styles.divider} />

        {/* 3. 상품 상세 정보 영역 */}
        <View style={styles.detailSection}>
          <Text style={styles.productTitle}>{product.title}</Text>
          <Text style={styles.productCategory}>{product.category}</Text>
          <Text style={styles.productDescription}>{product.description}</Text>

          <Text style={styles.metaInfo}>
            채팅 3 · 관심 {product.likes} · 조회 {product.views}
          </Text>
        </View>
      </ScrollView>

      {/* 4. 하단 고정 바 (찜, 가격, 액션 버튼) */}
      <View style={styles.bottomBar}>
        <TouchableOpacity
          style={styles.likeButton}
          onPress={() => setIsLiked(!isLiked)}
        >
          <Ionicons
            name={isLiked ? 'heart' : 'heart-outline'}
            size={28}
            color={isLiked ? '#FF5252' : '#888'}
          />
        </TouchableOpacity>

        <View style={styles.priceContainer}>
          <Text style={styles.priceText}>
            {product.price.toLocaleString()}원
          </Text>
        </View>

        {/* 버튼 두 개 (장바구니 / 채팅하기) */}
        <View style={styles.actionButtons}>
          <TouchableOpacity style={styles.cartButton}>
            <Text style={styles.cartButtonText}>장바구니</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.chatButton}>
            <Text style={styles.chatButtonText}>채팅하기</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  scrollArea: {
    flex: 1,
  },
  imageContainer: {
    width: width,
    height: width, // 이미지를 정사각형으로 꽉 채움
    position: 'relative',
  },
  productImage: {
    width: '100%',
    height: '100%',
    resizeMode: 'cover',
  },
  backButton: {
    position: 'absolute',
    top: 15,
    left: 15,
    backgroundColor: 'rgba(255, 255, 255, 0.8)',
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  profileSection: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 20,
  },
  profileImage: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: '#F0F0F0',
  },
  profileInfo: {
    flex: 1,
    marginLeft: 15,
  },
  profileName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  profileLocation: {
    fontSize: 13,
    color: '#888',
  },
  ratingBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF9E6',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 15,
  },
  ratingText: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#333',
    marginLeft: 4,
  },
  divider: {
    height: 1,
    backgroundColor: '#F0F0F0',
    marginHorizontal: 20,
  },
  detailSection: {
    padding: 20,
    paddingBottom: 40,
  },
  productTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    lineHeight: 28,
    marginBottom: 8,
  },
  productCategory: {
    fontSize: 13,
    color: '#888',
    marginBottom: 20,
  },
  productDescription: {
    fontSize: 16,
    lineHeight: 24,
    color: '#444',
    marginBottom: 30,
  },
  metaInfo: {
    fontSize: 13,
    color: '#888',
  },
  bottomBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 15,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
    backgroundColor: '#fff',
  },
  likeButton: {
    paddingRight: 15,
    borderRightWidth: 1,
    borderRightColor: '#E0E0E0',
  },
  priceContainer: {
    flex: 1,
    paddingLeft: 15,
  },
  priceText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  actionButtons: {
    flexDirection: 'row',
    gap: 10,
  },
  cartButton: {
    paddingHorizontal: 15,
    paddingVertical: 12,
    borderRadius: 8,
    backgroundColor: '#E8F5E9', // 옅은 초록색
  },
  cartButtonText: {
    color: '#00A859',
    fontWeight: 'bold',
    fontSize: 15,
  },
  chatButton: {
    paddingHorizontal: 15,
    paddingVertical: 12,
    borderRadius: 8,
    backgroundColor: '#00A859',
  },
  chatButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 15,
  },
});
