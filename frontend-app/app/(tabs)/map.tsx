import React, { useState, useRef, useEffect } from 'react';
import { 
  StyleSheet, View, ScrollView, TouchableOpacity, Alert, 
  ActivityIndicator, TextInput, Keyboard, FlatList 
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '../../components/CustomText';
import { WebView, WebViewMessageEvent } from 'react-native-webview';
import * as Location from 'expo-location'; 
import { useRouter } from 'expo-router'; 

// API & Constants
import { shopApi } from '../../api/shop'; 
import { regionApi } from '../../api/region'; 
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';

// 리팩토링으로 분리된 모듈 불러오기
import { getKakaoMapHtml } from '../../constants/kakaoMapHtml';
import { useDebounce } from '../../hooks/useDebounce';
import ShopBottomSheet from '../../components/map/ShopBottomSheet';

export default function MapScreen() {
  const router = useRouter();
  const webviewRef = useRef<WebView>(null);
  const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY || '';
  const shopsRef = useRef<any[]>([]);
  
  // 1. 상태 관리
  const [activeCategoryId, setActiveCategoryId] = useState<number>(0);
  const [isLoading, setIsLoading] = useState(false);
  const [currentCenter, setCurrentCenter] = useState<{lat: number, lng: number} | null>(null);
  const [selectedShop, setSelectedShop] = useState<any | null>(null);
  
  const [isSearching, setIsSearching] = useState<boolean>(false);
  const [searchText, setSearchText] = useState<string>('');
  const [searchResults, setSearchResults] = useState<any[]>([]); 

  // 2. 분리해둔 커스텀 훅 사용
  const debouncedSearchText = useDebounce(searchText, 300);

  // 3. 생명주기 (Effect)
  useEffect(() => {
    if (!debouncedSearchText.trim() || !currentCenter) {
      setSearchResults([]);
      return;
    }

    const fetchSearchResults = async () => {
      try {
        // 1. 현재 지도 좌표로 동네 찾기
        const regionRes = await regionApi.getNearbyRegions(currentCenter.lat, currentCenter.lng);
        const nearbyRegions = regionRes.data || regionRes || [];
        
        if (nearbyRegions.length === 0) {
          setSearchResults([]);
          return;
        }

        const currentRegionId = nearbyRegions[0].regionId;

        // 2. 찾은 동네 ID로 상점 검색
        const shopRes = await shopApi.getShops({ 
          regionId: currentRegionId,
          keyword: debouncedSearchText.trim(),
          size: 15 
        });
        
        const data = shopRes.content || shopRes.data?.content || shopRes.data || shopRes || [];
        setSearchResults(data);
      } catch (error) {
        console.error("검색 API 에러:", error);
      }
    };

    fetchSearchResults();
  }, [debouncedSearchText, currentCenter]);

  useEffect(() => {
    if (currentCenter && !isSearching) {
      setSelectedShop(null);
      fetchShopsInArea(currentCenter.lat, currentCenter.lng, activeCategoryId);
    }
  }, [activeCategoryId]);

  // 4. 이벤트 핸들러 모음
  const handleCloseModal = () => {
    setSelectedShop(null);
    Keyboard.dismiss();

    if (searchText !== '' || isSearching) {
      setSearchText('');
      setIsSearching(false);
      setSearchResults([]);
      if (currentCenter) {
        fetchShopsInArea(currentCenter.lat, currentCenter.lng, activeCategoryId);
      }
    }
  };

  const onWebViewMessage = (event: WebViewMessageEvent) => {
    const data = event.nativeEvent.data;

    if (data.startsWith('CLICK_SHOP:')) {
      const shopId = Number(data.split(':')[1]);
      const clickedShop = shopsRef.current.find(s => s.storeId === shopId);
      if (clickedShop) setSelectedShop(clickedShop);
    } else if (data === 'MAP_CLICKED') {
      handleCloseModal();
    } else if (data.startsWith('MAP_MOVED:')) {
      const [, lat, lng] = data.split(':');
      const newLat = Number(lat);
      const newLng = Number(lng);
      setCurrentCenter({ lat: newLat, lng: newLng });
      
      if (!isSearching && !searchText) {
        fetchShopsInArea(newLat, newLng, activeCategoryId);
      }
    } else if (data === 'MAP_READY') {
      setInitialLocation();
    }
  };

  const fetchShopsInArea = async (lat: number, lng: number, catId: number) => {
    try {
      // 1단계: 지도가 멈춘 곳의 위경도로 주변 동네 ID 가져오기
      const regionRes = await regionApi.getNearbyRegions(lat, lng);
      const nearbyRegions = regionRes.data || regionRes || []; 
      
      // 만약 조회된 동네가 없으면(빈 배열), 마커를 지우고 함수 종료
      if (nearbyRegions.length === 0) {
        shopsRef.current = [];
        webviewRef.current?.injectJavaScript(`window.renderShops('[]'); true;`);
        return; 
      }

      const currentRegionId = nearbyRegions[0].regionId; 
      const apiCategoryId = catId === 0 ? undefined : catId;

      // 2단계: 동네 ID와 카테고리를 넣어 실제 상점들 조회
      const shopRes = await shopApi.getShops({ 
        regionId: currentRegionId,
        categoryId: apiCategoryId,
        size: 50 
      });
      
      const realShops = shopRes.content || shopRes.data?.content || shopRes.data || shopRes || [];
      shopsRef.current = realShops;

      // 3단계: 조회된 상점을 지도 웹뷰로 주입하여 렌더링
      const safeJson = JSON.stringify(realShops).replace(/'/g, "\\'");
      const runJS = `window.renderShops('${safeJson}'); true;`;
      webviewRef.current?.injectJavaScript(runJS);
      
    } catch (e) {
      console.error('지도 상점 로딩 실패:', e);
    }
  };

  const handleSelectSearchResult = (shop: any) => {
    Keyboard.dismiss();
    setSearchText(shop.name); 
    setSearchResults([]); 
    setActiveCategoryId(0);
    shopsRef.current = [shop]; 
    
    const moveJS = `window.moveToLocation(${shop.latitude}, ${shop.longitude}); true;`;
    webviewRef.current?.injectJavaScript(moveJS);

    const safeJson = JSON.stringify([shop]).replace(/'/g, "\\'");
    const runJS = `window.renderShops('${safeJson}'); true;`;
    webviewRef.current?.injectJavaScript(runJS);

    setSelectedShop(shop);
  };

  const setInitialLocation = async () => {
    try {
      setIsLoading(true);
      const res = await regionApi.getMyRegions();
      const regions = res.data || [];
      const primaryRegion = regions.find((r: any) => r.isPrimary === true);

      if (primaryRegion && primaryRegion.latitude && primaryRegion.longitude) {
        setCurrentCenter({ lat: primaryRegion.latitude, lng: primaryRegion.longitude });
        const runJS = `window.moveToLocation(${primaryRegion.latitude}, ${primaryRegion.longitude}); true;`;
        webviewRef.current?.injectJavaScript(runJS);
        fetchShopsInArea(primaryRegion.latitude, primaryRegion.longitude, activeCategoryId);
      } else {
        await handleMyLocation();
      }
    } catch (error) {
      await handleMyLocation();
    } finally {
      setIsLoading(false);
    }
  };

  const handleMyLocation = async () => {
    try {
      setIsLoading(true);
      setSearchText('');
      setIsSearching(false);
      setSearchResults([]);
      setSelectedShop(null); 

      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return Alert.alert('권한 필요', '위치 권한이 필요합니다.');

      const location = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCurrentCenter({ lat: location.coords.latitude, lng: location.coords.longitude }); 
      
      const runJS = `window.moveToLocation(${location.coords.latitude}, ${location.coords.longitude}); true;`;
      webviewRef.current?.injectJavaScript(runJS);

      fetchShopsInArea(location.coords.latitude, location.coords.longitude, activeCategoryId);
    } catch (error) {
      Alert.alert('오류', '위치를 불러올 수 없습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const getCategoryName = (id: number) => {
    return SHOP_CATEGORIES.find(c => c.id === id)?.name || '기타';
  };

  // 5. 렌더링
  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 & 검색바 */}
      <View style={styles.header}>
        {isSearching ? (
          <View style={styles.searchBarContainer}>
            <TouchableOpacity 
              style={styles.searchBackBtn}
              onPress={() => {
                setIsSearching(false);
                setSearchText('');
                setSearchResults([]);
                setSelectedShop(null);
                if (currentCenter) fetchShopsInArea(currentCenter.lat, currentCenter.lng, activeCategoryId);
              }}
            >
              <Ionicons name="chevron-back" size={24} color="#333" />
            </TouchableOpacity>

            <TextInput
              style={styles.searchInput}
              value={searchText}
              onChangeText={setSearchText}
              placeholder="상점 이름을 검색해 보세요"
              returnKeyType="search"
              autoFocus
            />
            
            {searchText.length > 0 && (
              <TouchableOpacity onPress={() => {
                setSearchText('');
                setSearchResults([]);
                setSelectedShop(null); 
                if (currentCenter) fetchShopsInArea(currentCenter.lat, currentCenter.lng, activeCategoryId);
              }} style={{ padding: 4 }}>
                <Ionicons name="close-circle" size={20} color="#CCC" />
              </TouchableOpacity>
            )}
          </View>
        ) : (
          <>
            <Text style={styles.headerTitle}>이음지도</Text>
            <TouchableOpacity onPress={() => setIsSearching(true)}>
              <Ionicons name="search" size={24} color="#333" />
            </TouchableOpacity>
          </>
        )}
      </View>

      {/* 카테고리 탭 */}
      <View style={styles.categoryContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 20 }}>
          {SHOP_CATEGORIES.map((cat) => (
            <TouchableOpacity 
              key={cat.id} 
              style={[styles.categoryBtn, activeCategoryId === cat.id && styles.categoryBtnActive]}
              onPress={() => {
                setActiveCategoryId(cat.id);
                setSearchText(''); 
                setIsSearching(false);
                setSearchResults([]);
                setSelectedShop(null);
              }}
            >
              <Text style={[styles.categoryText, activeCategoryId === cat.id && styles.categoryTextActive]}>
                {cat.name}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* 지도 영역 */}
      <View style={styles.mapArea}>
        <WebView
          ref={webviewRef}
          originWhitelist={['*']}
          source={{ html: getKakaoMapHtml(KAKAO_JS_KEY), baseUrl: 'https://eeum.app/' }} 
          style={{ flex: 1 }}
          javaScriptEnabled={true}
          onMessage={onWebViewMessage}
        />

        {/* 연관 검색어 드롭다운 */}
        {isSearching && searchResults.length > 0 && (
          <View style={styles.searchResultsContainer}>
            <FlatList
              data={searchResults}
              keyExtractor={(item) => item.storeId.toString()}
              keyboardShouldPersistTaps="handled" 
              renderItem={({ item }) => (
                <TouchableOpacity style={styles.searchResultItem} onPress={() => handleSelectSearchResult(item)}>
                  <Ionicons name="search-outline" size={16} color="#888" style={{ marginRight: 10 }} />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.searchResultName} numberOfLines={1}>{item.name}</Text>
                  </View>
                  <Text style={styles.searchResultCategory}>{getCategoryName(item.categoryId)}</Text>
                </TouchableOpacity>
              )}
            />
          </View>
        )}

        <TouchableOpacity style={[styles.myLocationBtn, selectedShop && { bottom: 180 }]} onPress={handleMyLocation} disabled={isLoading}>
          {isLoading ? <ActivityIndicator size="small" color="#333" /> : (
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <Ionicons name="locate" size={16} color="#00A859" style={{ marginRight: 6 }} />
              <Text style={styles.myLocationText}>내 위치로 이동</Text>
            </View>
          )}
        </TouchableOpacity>

        {/* 5. 분리된 바텀 시트 컴포넌트 렌더링 */}
        {selectedShop && (
          <ShopBottomSheet 
            shop={selectedShop}
            categoryName={getCategoryName(selectedShop.categoryId)}
            onClose={handleCloseModal}
            onPressDetail={(id) => router.push(`/shop/${id}` as any)}
          />
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 10, minHeight: 60 },
  headerTitle: { fontSize: 20, fontWeight: 'bold', color: '#333' },
  searchBarContainer: { flex: 1, flexDirection: 'row', alignItems: 'center', backgroundColor: '#F5F5F5', borderRadius: 8, paddingHorizontal: 12, height: 45 },
  searchBackBtn: { marginRight: 8 },
  searchInput: { flex: 1, fontSize: 16, color: '#333', paddingVertical: 0, height: '100%' },
  searchResultsContainer: {
    position: 'absolute', top: 10, left: 20, right: 20,
    backgroundColor: '#fff', borderRadius: 12, maxHeight: 250, zIndex: 999,
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.15, shadowRadius: 10, elevation: 10,
  },
  searchResultItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 16, paddingHorizontal: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  searchResultName: { fontSize: 15, color: '#333' },
  searchResultCategory: { fontSize: 12, color: '#888' },
  categoryContainer: { paddingBottom: 15 },
  categoryBtn: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: '#F5F5F5', marginRight: 8 },
  categoryBtnActive: { backgroundColor: '#00A859' },
  categoryText: { color: '#666', fontWeight: '600' },
  categoryTextActive: { color: '#fff' },
  mapArea: { flex: 1, backgroundColor: '#F8F9FA', position: 'relative' },
  myLocationBtn: { position: 'absolute', bottom: 30, alignSelf: 'center', backgroundColor: '#fff', paddingHorizontal: 20, paddingVertical: 12, borderRadius: 25, shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 5, elevation: 5 },
  myLocationText: { fontSize: 14, fontWeight: 'bold', color: '#333' }
});