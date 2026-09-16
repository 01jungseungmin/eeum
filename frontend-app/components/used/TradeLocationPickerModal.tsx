import React, { useRef, useState } from 'react';
import {
  Modal, View, StyleSheet, TextInput, TouchableOpacity,
  FlatList, Keyboard, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { WebView, WebViewMessageEvent } from 'react-native-webview';
import { Text } from '../CustomText';
import { getKakaoLocationPickerHtml } from '../../constants/kakaoLocationPickerHtml';
import { useDebounce } from '../../hooks/useDebounce';

export interface TradeLocationSelection {
  name: string;
  latitude: number;
  longitude: number;
  placeId: string | null;
}

interface SearchResult {
  placeId: string;
  placeName: string;
  address: string;
  latitude: number;
  longitude: number;
}

interface Props {
  visible: boolean;
  initialCenter: { lat: number; lng: number } | null;
  onClose: () => void;
  onConfirm: (selection: TradeLocationSelection) => void;
}

// 중고거래 글 작성 화면에서 "대략" 거래 장소를 고르는 모달.
// 검색 결과를 고르거나 지도를 직접 탭해 핀을 찍을 수 있다 — 핀만 찍은 경우
// 카카오 장소 ID가 없으므로 장소명은 사용자가 직접 입력해야 한다.
export default function TradeLocationPickerModal({ visible, initialCenter, onClose, onConfirm }: Props) {
  const webviewRef = useRef<WebView>(null);
  const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY || '';

  const [isMapReady, setIsMapReady] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
  const debouncedSearchText = useDebounce(searchText, 300);

  const [placeName, setPlaceName] = useState('');
  const [selected, setSelected] = useState<{ latitude: number; longitude: number; placeId: string | null } | null>(null);

  React.useEffect(() => {
    if (!isMapReady) return;
    webviewRef.current?.injectJavaScript(`window.searchPlaces(${JSON.stringify(debouncedSearchText)}); true;`);
  }, [debouncedSearchText, isMapReady]);

  React.useEffect(() => {
    if (!visible) {
      setSearchText('');
      setSearchResults([]);
      setPlaceName('');
      setSelected(null);
      setIsMapReady(false);
    }
  }, [visible]);

  const onWebViewMessage = (event: WebViewMessageEvent) => {
    try {
      const msg = JSON.parse(event.nativeEvent.data);
      if (msg.type === 'MAP_READY') {
        setIsMapReady(true);
        if (initialCenter) {
          webviewRef.current?.injectJavaScript(
            `window.moveToLocation(${initialCenter.lat}, ${initialCenter.lng}); true;`
          );
        }
      } else if (msg.type === 'SEARCH_RESULTS') {
        setSearchResults(msg.payload);
      } else if (msg.type === 'PIN_SELECTED') {
        // 지도를 직접 탭한 경우 — 장소명은 비워두고 사용자가 입력하게 한다.
        setSearchResults([]);
        Keyboard.dismiss();
        setSelected({ latitude: msg.payload.latitude, longitude: msg.payload.longitude, placeId: null });
        setPlaceName(msg.payload.address || '');
      }
    } catch {
      // 지도 내부 로그 등 JSON이 아닌 메시지는 무시한다.
    }
  };

  const handleSelectResult = (item: SearchResult) => {
    Keyboard.dismiss();
    setSearchText(item.placeName);
    setSearchResults([]);
    setPlaceName(item.placeName);
    setSelected({ latitude: item.latitude, longitude: item.longitude, placeId: item.placeId });
    webviewRef.current?.injectJavaScript(
      `window.selectPlace(${item.latitude}, ${item.longitude}); true;`
    );
  };

  const canConfirm = !!selected && placeName.trim().length > 0;

  const handleConfirm = () => {
    if (!canConfirm || !selected) return;
    onConfirm({
      name: placeName.trim(),
      latitude: selected.latitude,
      longitude: selected.longitude,
      placeId: selected.placeId,
    });
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
        <View style={styles.header}>
          <TouchableOpacity onPress={onClose} style={styles.headerIcon}>
            <Ionicons name="close" size={24} color="#333" />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>거래 장소 선택</Text>
          <View style={styles.headerIcon} />
        </View>

        <View style={styles.searchBarContainer}>
          <Ionicons name="search" size={18} color="#999" style={{ marginRight: 8 }} />
          <TextInput
            style={styles.searchInput}
            value={searchText}
            onChangeText={setSearchText}
            placeholder="장소를 검색해 보세요 (예: OO역 3번 출구)"
            placeholderTextColor="#999"
            returnKeyType="search"
          />
        </View>

        <View style={styles.mapArea}>
          <WebView
            ref={webviewRef}
            originWhitelist={['*']}
            source={{ html: getKakaoLocationPickerHtml(KAKAO_JS_KEY), baseUrl: 'https://eeum.app/' }}
            style={{ flex: 1 }}
            javaScriptEnabled
            onMessage={onWebViewMessage}
          />
          {!isMapReady && (
            <View style={styles.mapLoading}>
              <ActivityIndicator size="small" color="#00A859" />
            </View>
          )}

          {searchResults.length > 0 && (
            <View style={styles.resultsContainer}>
              <FlatList
                data={searchResults}
                keyExtractor={(item) => item.placeId}
                keyboardShouldPersistTaps="handled"
                renderItem={({ item }) => (
                  <TouchableOpacity style={styles.resultItem} onPress={() => handleSelectResult(item)}>
                    <Text style={styles.resultName} numberOfLines={1}>{item.placeName}</Text>
                    <Text style={styles.resultAddress} numberOfLines={1}>{item.address}</Text>
                  </TouchableOpacity>
                )}
              />
            </View>
          )}
        </View>

        <View style={styles.bottomPanel}>
          <Text style={styles.label}>장소명</Text>
          <TextInput
            style={styles.nameInput}
            value={placeName}
            onChangeText={setPlaceName}
            placeholder="검색 결과를 고르거나 지도를 눌러 장소를 찍어주세요"
            placeholderTextColor="#999"
            maxLength={255}
          />

          <View style={styles.bottomButtonRow}>
            <TouchableOpacity style={styles.clearButton} onPress={onClose}>
              <Text style={styles.clearButtonText}>취소</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.confirmButton, !canConfirm && styles.confirmButtonDisabled]}
              onPress={handleConfirm}
              disabled={!canConfirm}
            >
              <Text style={styles.confirmButtonText}>이 장소로 선택</Text>
            </TouchableOpacity>
          </View>
        </View>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, height: 56, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  headerIcon: { width: 40 },
  headerTitle: { fontSize: 16, fontWeight: 'bold', color: '#333' },
  searchBarContainer: { flexDirection: 'row', alignItems: 'center', margin: 16, paddingHorizontal: 14, height: 44, backgroundColor: '#F5F5F5', borderRadius: 8 },
  searchInput: { flex: 1, fontSize: 14, color: '#333' },
  mapArea: { flex: 1, position: 'relative' },
  mapLoading: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, justifyContent: 'center', alignItems: 'center' },
  resultsContainer: {
    position: 'absolute', top: 10, left: 16, right: 16,
    backgroundColor: '#fff', borderRadius: 12, maxHeight: 260, zIndex: 999,
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.15, shadowRadius: 10, elevation: 10,
  },
  resultItem: { paddingVertical: 12, paddingHorizontal: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  resultName: { fontSize: 14, color: '#333', fontWeight: '600' },
  resultAddress: { fontSize: 12, color: '#888', marginTop: 2 },
  bottomPanel: { padding: 16, borderTopWidth: 1, borderTopColor: '#F0F0F0' },
  label: { fontSize: 13, fontWeight: 'bold', color: '#333', marginBottom: 8 },
  nameInput: { backgroundColor: '#F5F5F5', borderRadius: 8, paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: '#333' },
  bottomButtonRow: { flexDirection: 'row', marginTop: 12 },
  clearButton: { flex: 1, height: 48, borderRadius: 8, backgroundColor: '#F5F5F5', justifyContent: 'center', alignItems: 'center', marginRight: 8 },
  clearButtonText: { fontSize: 15, fontWeight: 'bold', color: '#666' },
  confirmButton: { flex: 2, height: 48, borderRadius: 8, backgroundColor: '#00A859', justifyContent: 'center', alignItems: 'center' },
  confirmButtonDisabled: { backgroundColor: '#9ED9BD' },
  confirmButtonText: { fontSize: 15, fontWeight: 'bold', color: '#FFF' },
});
