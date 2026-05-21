import React, { useState, useEffect } from 'react';
import {
  View, StyleSheet, TouchableOpacity, FlatList,
  Image, ScrollView, Dimensions, Modal, Pressable, Alert
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { regionService } from '../../services/regionService';
import { Text } from '../../components/CustomText';
import { regionApi } from '../../api/region';

const { width } = Dimensions.get('window');

// ==========================================
// 📦 1. 데이터 영역 (나중에는 서버 통신이나 constants 폴더로 분리)
// ==========================================
const SHOP_LIST = [{ id: 's1', name: '라떼가 맛있는 집', category: '카페', img: 'https://via.placeholder.com/150/333333/FFFFFF?text=Cafe' }, /*...생략된 더미 데이터들...*/];
const EVENT_PRODUCTS = [{ id: 'e1', name: '무항생제 계란 30구', price: 6500, img: 'https://via.placeholder.com/150/EEEEEE/888888?text=Egg' }];
const USED_CATEGORIES = ['전체', '농산물', '의류', '잡화', '가전'];
const USED_PRODUCTS = [{ id: 'u1', title: '잔치국수 냄비', location: '송파동', price: 5000, likes: 12, chats: 2, img: 'https://via.placeholder.com/150/E8F5E9/00A859?text=Pot' }];

// ==========================================
// 🧩 2. 하위 컴포넌트 영역 (나중에는 components 폴더로 독립시킬 파일들)
// ==========================================

// 2-1. 헤더 컴포넌트
const HomeHeader = ({ primaryRegionName, onOpenModal, activeTab, setActiveTab, onSearch }: any) => (
  <View style={styles.headerContainer}>
    <View style={styles.headerTop}>
      <TouchableOpacity style={styles.locationSelector} onPress={onOpenModal}>
        <Ionicons name="location-sharp" size={18} color="#00A859" />
        <Text style={styles.headerLocationText}>{primaryRegionName}</Text>
        <Ionicons name="chevron-down" size={16} color="#333" />
      </TouchableOpacity>
      <View style={styles.headerIcons}>
        <TouchableOpacity style={{ marginRight: 15 }}><Ionicons name="notifications-outline" size={24} color="#333" /></TouchableOpacity>
        <TouchableOpacity><Ionicons name="cart-outline" size={24} color="#333" /></TouchableOpacity>
      </View>
    </View>

    <View style={styles.toggleContainer}>
      <TouchableOpacity style={[styles.toggleBtn, activeTab === 'shop' && styles.toggleBtnActive]} onPress={() => setActiveTab('shop')}>
        <Text style={[styles.toggleText, activeTab === 'shop' && styles.toggleTextActive]}>전체상점</Text>
      </TouchableOpacity>
      <TouchableOpacity style={[styles.toggleBtn, activeTab === 'used' && styles.toggleBtnActive]} onPress={() => setActiveTab('used')}>
        <Text style={[styles.toggleText, activeTab === 'used' && styles.toggleTextActive]}>중고거래</Text>
      </TouchableOpacity>
    </View>

    <TouchableOpacity style={styles.searchBar} onPress={onSearch}>
      <Text style={styles.searchText}>검색어를 입력해주세요</Text>
      <Ionicons name="search" size={20} color="#00A859" />
    </TouchableOpacity>
  </View>
);

// 2-2. 동네 상점 뷰 컴포넌트
const ShopView = ({ router }: any) => (
  <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 30 }}>
    <View style={styles.bannerPlaceholder}><Text style={{ color: '#fff' }}>이벤트 배너 영역</Text></View>
    <View style={styles.sectionContainer}>
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>우리 동네 상점</Text>
        <Ionicons name="chevron-forward" size={20} color="#333" />
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false}>
        {SHOP_LIST.map((shop) => (
          <TouchableOpacity key={shop.id} style={styles.shopCard} onPress={() => router.push({ pathname: '/shop/[id]', params: { id: shop.id } })}>
            <Image source={{ uri: shop.img }} style={styles.shopImage} />
            <Text style={styles.shopName}>{shop.name}</Text>
            <Text style={styles.shopCategory}>{shop.category}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  </ScrollView>
);

// 2-3. 중고거래 뷰 컴포넌트
const UsedTradeView = ({ router, selectedCategory, setSelectedCategory }: any) => (
  <View style={{ flex: 1 }}>
    <View style={styles.categoryContainer}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 15 }}>
        {USED_CATEGORIES.map((cat) => (
          <TouchableOpacity key={cat} style={[styles.categoryPill, selectedCategory === cat && styles.categoryPillActive]} onPress={() => setSelectedCategory(cat)}>
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
        <TouchableOpacity style={styles.usedProductCard} onPress={() => router.push({ pathname: '/product/[id]', params: { id: item.id } })}>
          <Image source={{ uri: item.img }} style={styles.usedProductImage} />
          <Text style={styles.usedProductTitle} numberOfLines={1}>{item.title}</Text>
          <Text style={styles.usedProductPrice}>{item.price.toLocaleString()}원</Text>
        </TouchableOpacity>
      )}
    />
  </View>
);

// 2-4. 동네 설정 모달 컴포넌트 (✨ 삭제 기능 연결, 데이터 속성 이름 변경)
const RegionModal = ({ visible, onClose, regions, onSetPrimary, onAddRegion, onDeleteRegion }: any) => (
  <Modal animationType="slide" transparent={true} visible={visible} onRequestClose={onClose}>
    <Pressable style={styles.modalOverlay} onPress={onClose}>
      <Pressable style={styles.modalContent}>
        <Text style={styles.modalTitle}>내 동네 설정</Text>
        <Text style={styles.modalSubTitle}>최대 2개의 동네를 선택할 수 있어요</Text>
        
        {regions.map((item: any) => (
          <TouchableOpacity key={item.accountRegionId} style={styles.regionItem} onPress={() => onSetPrimary(item.accountRegionId)}>
            <View style={styles.regionLeft}>
              <View style={[styles.radio, item.isPrimary && styles.radioActive]} />
              {/* 💡 백엔드 데이터 형식에 맞춰 item.dong -> item.region.name 으로 변경했습니다 */}
              <Text style={item.isPrimary ? styles.regionNameActive : styles.regionName}>
                {item.region.name}
              </Text>
              {item.verified && <Ionicons name="checkmark-circle" size={14} color="#00A859" style={{marginLeft: 5}} />}
            </View>
            
            {/* ✨ X 버튼을 눌렀을 때 삭제 함수(onDeleteRegion)가 실행되도록 연결! */}
            <TouchableOpacity onPress={() => onDeleteRegion(item.accountRegionId)}>
              <Ionicons name="close" size={20} color="#999" />
            </TouchableOpacity>
          </TouchableOpacity>
        ))}

        <TouchableOpacity style={styles.addButton} onPress={onAddRegion}>
          <Ionicons name="add" size={20} color="#fff" />
          <Text style={styles.addButtonText}>동네 추가</Text>
        </TouchableOpacity>
      </Pressable>
    </Pressable>
  </Modal>
);


// ==========================================
// 🚀 3. 메인 부모 컴포넌트 (Controller 역할)
// ==========================================
export default function HomeScreen() {
  const router = useRouter();
  
  const [activeTab, setActiveTab] = useState<'shop' | 'used'>('shop');
  const [selectedCategory, setSelectedCategory] = useState('전체');
  const [modalVisible, setModalVisible] = useState(false);
  
  const [regions, setRegions] = useState<any[]>([]);
  const [primaryRegionName, setPrimaryRegionName] = useState('동네 로딩중...');

  useEffect(() => {
    loadRegions();
  }, []);

  // ✨ 1. 지역 목록 불러오기 (regionApi 사용)
  const loadRegions = async () => {
    try {
      const res = await regionApi.getMyRegions();
      // 서버 응답 구조(res.data)에 맞춰 수정
      const data = res.data || res; 
      setRegions(data);
      
      const primary = data.find((r: any) => r.isPrimary);
      // 💡 백엔드 데이터에 맞게 primary.region.name 으로 수정
      setPrimaryRegionName(primary ? primary.region.name : '동네 설정 필요');
    } catch (e) {
      console.log("지역 목록 로딩 실패:", e);
      setPrimaryRegionName('동네 설정 필요');
    }
  };

  // ✨ 2. 대표 지역 설정하기
  const handleSetPrimary = async (id: number) => {
    try {
      await regionApi.setPrimaryRegion(id);
      loadRegions(); // 설정 후 목록을 다시 불러와서 초록색 체크를 업데이트합니다.
    } catch (e) {
      Alert.alert("오류", "대표 지역 설정에 실패했습니다.");
    }
  };

  // ✨ 3. 지역 삭제하기 (새로 추가됨!)
  const handleDeleteRegion = (id: number) => {
    Alert.alert("삭제", "이 동네를 삭제하시겠습니까?", [
      { text: "취소", style: "cancel" },
      {
        text: "삭제",
        style: "destructive",
        onPress: async () => {
          try {
            await regionApi.deleteRegion(id);
            loadRegions(); // 삭제 후 목록 새로고침
          } catch (e) {
            Alert.alert("오류", "삭제에 실패했습니다.");
          }
        }
      }
    ]);
  };

  const handleAddRegion = () => {
    setModalVisible(false);
    router.push('/region-search');
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}> 
      <HomeHeader 
        primaryRegionName={primaryRegionName}
        onOpenModal={() => setModalVisible(true)}
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        onSearch={() => router.push({ pathname: '/search' })}
      />
      
      {activeTab === 'shop' 
        ? <ShopView router={router} /> 
        : <UsedTradeView router={router} selectedCategory={selectedCategory} setSelectedCategory={setSelectedCategory} />
      }

      <RegionModal 
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
        regions={regions}
        onSetPrimary={handleSetPrimary}
        onAddRegion={handleAddRegion}
        onDeleteRegion={handleDeleteRegion} // ✨ 모달에 삭제 함수 전달!
      />
    </SafeAreaView>
  );
}

// ==========================================
// 🎨 4. 스타일 영역 (길어서 접어두고 관리하세요)
// ==========================================
const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  headerContainer: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 15, backgroundColor: '#fff' },
  headerTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  locationSelector: { flexDirection: 'row', alignItems: 'center' },
  headerLocationText: { fontSize: 16, fontWeight: 'bold', color: '#333', marginHorizontal: 5 },
  headerIcons: { flexDirection: 'row' },
  toggleContainer: { flexDirection: 'row', backgroundColor: '#F5F5F5', borderRadius: 25, padding: 4, marginBottom: 15, width: 200 },
  toggleBtn: { flex: 1, paddingVertical: 8, alignItems: 'center', borderRadius: 20 },
  toggleBtnActive: { backgroundColor: '#00A859' },
  toggleText: { fontSize: 14, color: '#888', fontWeight: '600' },
  toggleTextActive: { color: '#fff' },
  searchBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#F5F5F5', paddingHorizontal: 15, paddingVertical: 12, borderRadius: 8 },
  searchText: { color: '#999', fontSize: 14 },
  bannerPlaceholder: { height: 180, backgroundColor: '#386641', justifyContent: 'center', alignItems: 'center', marginHorizontal: 20, borderRadius: 8, marginBottom: 25 },
  sectionContainer: { paddingLeft: 20, marginBottom: 30 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', paddingRight: 20, alignItems: 'center', marginBottom: 15 },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  shopCard: { marginRight: 15, width: 120 },
  shopImage: { width: 120, height: 120, borderRadius: 8, marginBottom: 8 },
  shopName: { fontSize: 15, fontWeight: '600', color: '#333', marginBottom: 2 },
  shopCategory: { fontSize: 12, color: '#888' },
  categoryContainer: { paddingVertical: 10 },
  categoryPill: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: '#F5F5F5', marginRight: 8 },
  categoryPillActive: { backgroundColor: '#00A859' },
  categoryPillText: { fontSize: 14, color: '#666' },
  categoryPillTextActive: { color: '#fff', fontWeight: 'bold' },
  usedProductCard: { width: (width - 45) / 2, marginBottom: 20 },
  usedProductImage: { width: '100%', height: (width - 45) / 2, borderRadius: 8, marginBottom: 10 },
  usedProductTitle: { fontSize: 15, fontWeight: '500', color: '#333', marginBottom: 4 },
  usedProductPrice: { fontSize: 15, fontWeight: 'bold', color: '#333' },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: '#fff', borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 25, paddingBottom: 40 },
  modalTitle: { fontSize: 18, fontWeight: 'bold', color: '#333', marginBottom: 5 },
  modalSubTitle: { fontSize: 13, color: '#888', marginBottom: 20 },
  regionItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  regionLeft: { flexDirection: 'row', alignItems: 'center' },
  radio: { width: 18, height: 18, borderRadius: 9, backgroundColor: '#E0E0E0', marginRight: 10 },
  radioActive: { backgroundColor: '#00A859' },
  regionName: { fontSize: 15, color: '#666' },
  regionNameActive: { fontSize: 15, color: '#333', fontWeight: 'bold' },
  addButton: { backgroundColor: '#00A859', flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 15, borderRadius: 8, marginTop: 20 },
  addButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold', marginLeft: 5 }
});