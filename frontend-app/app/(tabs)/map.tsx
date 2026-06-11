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
import { shopApi } from '../../api/shop'; 
import { regionApi } from '../../api/region'; 
import { SHOP_CATEGORIES } from '../../constants/shopDummyData';

export default function MapScreen() {
  const router = useRouter();
  
  const [activeCategoryId, setActiveCategoryId] = useState<number>(0);
  const [isLoading, setIsLoading] = useState(false);
  const [currentCenter, setCurrentCenter] = useState<{lat: number, lng: number} | null>(null);
  const [selectedShop, setSelectedShop] = useState<any | null>(null);
  
  const [isSearching, setIsSearching] = useState<boolean>(false);
  const [searchText, setSearchText] = useState<string>('');
  const [searchResults, setSearchResults] = useState<any[]>([]); 

  const shopsRef = useRef<any[]>([]);
  const webviewRef = useRef<WebView>(null);
  const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY;

  useEffect(() => {
    if (!searchText.trim()) {
      setSearchResults([]);
      return;
    }

    const delayDebounceFn = setTimeout(async () => {
      try {
        const currentRegionId = 223; 
        const shopRes = await shopApi.getShops({ 
          regionId: currentRegionId,
          keyword: searchText.trim(),
          size: 15 
        });
        
        const data = shopRes.content || shopRes.data?.content || shopRes.data || shopRes || [];
        setSearchResults(data);
      } catch (error) {
        console.error("검색 API 에러:", error);
      }
    }, 300); 

    return () => clearTimeout(delayDebounceFn);
  }, [searchText]);

  useEffect(() => {
    if (currentCenter && !isSearching) {
      setSelectedShop(null);
      fetchShopsInArea(currentCenter.lat, currentCenter.lng, activeCategoryId);
    }
  }, [activeCategoryId]);

  const mapHtml = `
    <!DOCTYPE html>
    <html lang="ko">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <style>
        html, body { width: 100%; height: 100%; margin: 0; padding: 0; background-color: #F8F9FA; }
        #map { width: 100%; height: 100%; }
        .shop-marker {
          background: #fff; border: 2px solid #00A859; border-radius: 25px; padding: 6px 12px;
          display: flex; align-items: center; gap: 4px;
          font-size: 13px; font-weight: bold; color: #333; box-shadow: 0 3px 6px rgba(0,0,0,0.2);
          position: relative; bottom: 25px; white-space: nowrap; cursor: pointer;
        }
        .shop-marker::after {
          content: ''; position: absolute; bottom: -7px; left: 50%; margin-left: -6px;
          border-width: 7px 6px 0; border-style: solid; border-color: #00A859 transparent transparent transparent;
        }
        .marker-icon { font-size: 14px; }
      </style>
    </head>
    <body>
      <div id="map">지도 로딩 중...</div>
      <script>
        var map;
        var currentOverlays = [];
        var userMarker;

        function sendLog(message) {
          if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
            window.ReactNativeWebView.postMessage(message);
          }
        }

        window.clickShop = function(shopId) {
          sendLog('CLICK_SHOP:' + shopId);
        };

        function getCategoryIcon(catId) {
          if (catId === 1) return '🍽️'; 
          if (catId === 2) return '☕'; 
          if (catId === 3) return '🍱'; 
          if (catId === 4) return '🥩'; 
          if (catId === 5) return '🥐'; 
          if (catId === 6) return '🏪'; 
          return '📍'; 
        }

        function initMap() {
          if (typeof kakao === 'undefined') return;
          kakao.maps.load(function() {
            try {
              var mapContainer = document.getElementById('map');
              var mapOption = { center: new kakao.maps.LatLng(37.548, 127.073), level: 3 };
              map = new kakao.maps.Map(mapContainer, mapOption);
              userMarker = new kakao.maps.Marker();

              kakao.maps.event.addListener(map, 'idle', function() {
                var center = map.getCenter();
                sendLog('MAP_MOVED:' + center.getLat() + ':' + center.getLng());
              });

              kakao.maps.event.addListener(map, 'click', function() {
                sendLog('MAP_CLICKED');
              });

              window.moveToLocation = function(lat, lng) {
                var moveLatLon = new kakao.maps.LatLng(lat, lng);
                userMarker.setPosition(moveLatLon);
                userMarker.setMap(map);
                map.panTo(moveLatLon);
              };

              window.renderShops = function(shopsJson) {
                var shops = JSON.parse(shopsJson);
                currentOverlays.forEach(function(overlay) { overlay.setMap(null); });
                currentOverlays = [];

                shops.forEach(function(shop) {
                  var position = new kakao.maps.LatLng(shop.latitude, shop.longitude);
                  var icon = getCategoryIcon(shop.categoryId);
                  
                  var content = 
                    '<div class="shop-marker" onclick="window.clickShop(' + shop.storeId + ')">' +
                      '<span class="marker-icon">' + icon + '</span>' + 
                      '<span>' + shop.name + '</span>' +
                    '</div>';

                  var customOverlay = new kakao.maps.CustomOverlay({
                    position: position, content: content, clickable: true, yAnchor: 1
                  });
                  customOverlay.setMap(map);
                  currentOverlays.push(customOverlay);
                });
              };
              sendLog('MAP_READY');
            } catch (e) {
              sendLog('[map error] ' + e.message);
            }
          });
        }
        var script = document.createElement('script');
        script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&autoload=false';
        script.onload = initMap;
        document.head.appendChild(script);
      </script>
    </body>
    </html>
  `;

  // 모달을 닫을 때 실행되는 통합 함수
  const handleCloseModal = () => {
    setSelectedShop(null);
    Keyboard.dismiss();

    // 만약 검색어가 있었거나 검색 모드였다면 전부 리셋하고 지도를 원래대로 되돌림
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
      // 지도 빈 공간을 터치했을 때 모달 닫기 & 검색 리셋 함수 호출
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
      const currentRegionId = 223; 
      const apiCategoryId = catId === 0 ? undefined : catId;

      const shopRes = await shopApi.getShops({ 
        regionId: currentRegionId,
        categoryId: apiCategoryId,
        size: 50 
      });
      
      const realShops = shopRes.content || shopRes.data?.content || shopRes.data || shopRes || [];
      shopsRef.current = realShops;

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

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
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
              <TouchableOpacity 
                onPress={() => {
                  setSearchText('');
                  setSearchResults([]);
                  setSelectedShop(null); 
                  if (currentCenter) fetchShopsInArea(currentCenter.lat, currentCenter.lng, activeCategoryId);
                }} 
                style={{ padding: 4 }}
              >
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

      <View style={styles.mapArea}>
        <WebView
          ref={webviewRef}
          originWhitelist={['*']}
          source={{ html: mapHtml, baseUrl: 'https://eeum.app/' }} 
          style={{ flex: 1 }}
          javaScriptEnabled={true}
          onMessage={onWebViewMessage}
        />

        {isSearching && searchResults.length > 0 && (
          <View style={styles.searchResultsContainer}>
            <FlatList
              data={searchResults}
              keyExtractor={(item) => item.storeId.toString()}
              keyboardShouldPersistTaps="handled" 
              renderItem={({ item }) => (
                <TouchableOpacity 
                  style={styles.searchResultItem}
                  onPress={() => handleSelectSearchResult(item)}
                >
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

        {selectedShop && (
          <View style={styles.bottomSheet}>
            {/* 모달의 X 버튼을 눌렀을 때도 검색 리셋 함수 호출 */}
            <TouchableOpacity style={styles.closeBtn} onPress={handleCloseModal}>
              <Ionicons name="close" size={24} color="#666" />
            </TouchableOpacity>

            <View style={styles.sheetContent}>
              <View style={styles.sheetInfo}>
                <Text style={styles.sheetCategory}>{getCategoryName(selectedShop.categoryId)}</Text>
                <Text fontWeight="bold" style={styles.sheetTitle}>{selectedShop.name}</Text>
                
                <View style={styles.sheetRatingRow}>
                  <Ionicons name="star" size={16} color="#FFD700" />
                  <Text fontWeight="bold" style={styles.sheetRating}>{selectedShop.rating || '0.0'}</Text>
                  <Text style={styles.sheetReviewCount}> 리뷰 {selectedShop.reviewCount || 0}</Text>
                </View>

                <View style={styles.sheetAddressRow}>
                  <Ionicons name="location-outline" size={14} color="#888" />
                  <Text style={styles.sheetAddress} numberOfLines={1}>{selectedShop.address}</Text>
                </View>
              </View>
            </View>

            <TouchableOpacity 
              style={styles.sheetDetailBtn}
              onPress={() => router.push(`/shop/${selectedShop.storeId}` as any)}
            >
              <Text fontWeight="bold" style={styles.sheetDetailBtnText}>매장 상세 보기</Text>
            </TouchableOpacity>
          </View>
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
  myLocationText: { fontSize: 14, fontWeight: 'bold', color: '#333' },

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