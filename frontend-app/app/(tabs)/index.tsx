import React, { useState, useCallback } from 'react';
import { StyleSheet, Alert, ActivityIndicator, View } from 'react-native';
import { useRouter, useFocusEffect } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as Location from 'expo-location';

import HomeHeader from '../../components/home/HomeHeader';
import ShopView from '../../components/home/ShopView';
import UsedTradeView from '../../components/home/UsedTradeView';
import RegionModal from '../../components/home/RegionModal';

import { regionApi } from '../../api/region';
import { blockIfDemo } from '../../utils/demoAccount';

export default function HomeScreen() {
  const router = useRouter();
  
  const [activeTab, setActiveTab] = useState<'shop' | 'used'>('shop');
  const [selectedCategory, setSelectedCategory] = useState('전체');
  const [modalVisible, setModalVisible] = useState(false);
  
  const [regions, setRegions] = useState<any[]>([]);
  // gunGu는 AI 노출 조회에 쓴다. 서버가 가게 주소 문자열로 거르기 때문에
  // 동 이름("연남동")이 아니라 구 이름("마포구")이어야 걸린다.
  const [viewingRegion, setViewingRegion] = useState<{id: number, name: string, gunGu?: string} | null>(null);
  
  const [isRegionLoading, setIsRegionLoading] = useState(false);

  useFocusEffect(
    useCallback(() => {
      loadRegions();
    }, [])
  );

  const loadRegions = async () => {
    try {
      const res = await regionApi.getMyRegions();
      const data = (res.success && res.data) ? res.data : []; 

      const normalizedData = data.map((item: any) => {
        const actualAccountRegionId = item.accountRegionId || item.id; 
        return {
          ...item,
          accountRegionId: actualAccountRegionId, 
          id: actualAccountRegionId,
          dong: item.dong || item.region?.dong || item.name || '동네 정보 없음',
          gunGu: item.gunGu || item.region?.gunGu || '',
          fullName: item.fullName || item.region?.fullName || ''
        };
      });

      setRegions(normalizedData);
      
      const primary = normalizedData.find((r: any) => r.isPrimary);
    
      if (primary && !viewingRegion) {
        setViewingRegion({ id: primary.regionId || primary.id, name: primary.dong || primary.fullName, gunGu: primary.gunGu });
      } else if (normalizedData.length === 0) {
        setViewingRegion(null);
      }
    } catch (e) {
      console.log("지역 목록 로딩 실패:", e);
      setRegions([]); 
      setViewingRegion(null);
    }
  };

  const handleSelectViewRegion = (region: any) => {
    setViewingRegion({ id: region.regionId || region.id, name: region.dong || region.fullName, gunGu: region.gunGu });
    setModalVisible(false);
  };

  const handleSetPrimary = async (id: number) => {
    if (blockIfDemo('대표 동네 변경')) return;
    if (isRegionLoading) return;
    setIsRegionLoading(true);
    try {
      await regionApi.setPrimaryRegion(id);
      await loadRegions(); 
      Alert.alert("성공", "대표 동네가 변경되었습니다.");
    } catch (e) {
      Alert.alert("오류", "대표 지역 설정에 실패했습니다.");
    } finally {
      setIsRegionLoading(false);
    }
  };

  const handleDeleteRegion = (id: number) => {
    if (blockIfDemo('동네 삭제')) return;
    if (isRegionLoading) return;
    Alert.alert("삭제", "이 동네를 삭제하시겠습니까?", [
      { text: "취소", style: "cancel" },
      {
        text: "삭제",
        style: "destructive",
        onPress: async () => {
          setIsRegionLoading(true);
          try {
            await regionApi.deleteRegion(id);
            if (viewingRegion?.id === id) setViewingRegion(null);
            await loadRegions(); 
          } catch (e) {
            Alert.alert("오류", "삭제에 실패했습니다.");
          } finally {
            setIsRegionLoading(false);
          }
        }
      }
    ]);
  };

  const handleVerifyRegion = async (id: number) => {
    if (blockIfDemo('동네 인증')) return;
    if (isRegionLoading) return;
    setIsRegionLoading(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert('권한 필요', '동네 인증을 진행하려면 위치 권한이 필요합니다.');
        return;
      }

      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      const { latitude, longitude } = location.coords;

      await regionApi.verifyRegion(id, latitude, longitude);
      
      Alert.alert("인증 성공", "현재 위치 인증이 완료되었습니다! 이제 대표 지역으로 설정할 수 있습니다.");
      await loadRegions(); 
    } catch (e: any) {
      const serverMessage = e.response?.data?.message || "현재 위치가 등록된 동네와 일치하지 않거나 에러가 발생했습니다.";
      Alert.alert("인증 실패", serverMessage);
    } finally {
      setIsRegionLoading(false);
    }
  };

  const handleAddRegion = () => {
    if (blockIfDemo('동네 추가')) return;
    if (regions.length >= 2) {
      Alert.alert(
        "동네 추가 불가", 
        "동네는 최대 2개까지만 등록할 수 있어요.\n새로운 동네를 추가하려면 기존 동네를 삭제해 주세요."
      );
      return;
    }
    setModalVisible(false);
    router.push('/region-search');
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}> 
      <HomeHeader 
        primaryRegionName={viewingRegion?.name || '동네 설정 필요'} 
        onOpenModal={() => setModalVisible(true)}
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        // 보고 있던 탭과 동네를 그대로 들고 간다 — 중고거래 탭에서 누른 검색이
        // 상점을 찾아주면 안 된다.
        onSearch={() => router.push({
          pathname: '/search',
          params: {
            scope: activeTab === 'used' ? 'USED' : 'STORE',
            ...(viewingRegion?.id ? { regionId: String(viewingRegion.id) } : {}),
          },
        })}
      />
      
      {activeTab === 'shop' 
        ? <ShopView router={router} regionId={viewingRegion?.id} regionKeyword={viewingRegion?.gunGu} /> 
        : <UsedTradeView router={router} regionId={viewingRegion?.id} />
      }

      <RegionModal 
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
        regions={regions}
        viewingRegionId={viewingRegion?.id}
        onSelectView={handleSelectViewRegion}
        onSetPrimary={handleSetPrimary}
        onAddRegion={handleAddRegion}
        onDeleteRegion={handleDeleteRegion}
        onVerifyRegion={handleVerifyRegion}
        isLoading={isRegionLoading}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' }
});