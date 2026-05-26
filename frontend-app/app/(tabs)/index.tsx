import React, { useState, useEffect } from 'react';
import { StyleSheet, Alert } from 'react-native';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

// 새로 분리한 컴포넌트들 Import
import HomeHeader from '../../components/home/HomeHeader';
import ShopView from '../../components/home/ShopView';
import UsedTradeView from '../../components/home/UsedTradeView';
import RegionModal from '../../components/home/RegionModal';

import { regionApi } from '../../api/region';

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

  // 1. 지역 목록 불러오기
  const loadRegions = async () => {
    try {
      const res = await regionApi.getMyRegions();
      const data = res.data || res; 
      setRegions(data);
      
      const primary = data.find((r: any) => r.isPrimary);
      setPrimaryRegionName(primary ? primary.region.name : '동네 설정 필요');
    } catch (e) {
      console.log("지역 목록 로딩 실패:", e);
      setPrimaryRegionName('동네 설정 필요');
    }
  };

  // 2. 대표 지역 설정하기
  const handleSetPrimary = async (id: number) => {
    try {
      await regionApi.setPrimaryRegion(id);
      loadRegions(); 
    } catch (e) {
      Alert.alert("오류", "대표 지역 설정에 실패했습니다.");
    }
  };

  // 3. 지역 삭제하기
  const handleDeleteRegion = (id: number) => {
    Alert.alert("삭제", "이 동네를 삭제하시겠습니까?", [
      { text: "취소", style: "cancel" },
      {
        text: "삭제",
        style: "destructive",
        onPress: async () => {
          try {
            await regionApi.deleteRegion(id);
            loadRegions(); 
          } catch (e) {
            Alert.alert("오류", "삭제에 실패했습니다.");
          }
        }
      }
    ]);
  };

  const handleAddRegion = () => {
    // 방어 로직: 현재 등록된 동네(regions)가 2개 이상이면 못 넘어감
    if (regions.length >= 2) {
      Alert.alert(
        "동네 추가 불가", 
        "동네는 최대 2개까지만 등록할 수 있어요.\n새로운 동네를 추가하려면 기존 동네를 삭제해 주세요."
      );
      return;
    }

    // 2개 미만일 때만 모달을 닫고 검색 화면으로 보냅니다.
    setModalVisible(false);
    router.push('/region-search');
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}> 
      {/* 1. 헤더 영역 */}
      <HomeHeader 
        primaryRegionName={primaryRegionName}
        onOpenModal={() => setModalVisible(true)}
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        onSearch={() => router.push({ pathname: '/search' })}
      />
      
      {/* 2. 컨텐츠 본문 영역 (탭에 따라 스위칭) */}
      {activeTab === 'shop' 
        ? <ShopView router={router} /> 
        : <UsedTradeView router={router} selectedCategory={selectedCategory} setSelectedCategory={setSelectedCategory} />
      }

      {/* 3. 내 동네 설정 모달 팝업 */}
      <RegionModal 
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
        regions={regions}
        onSetPrimary={handleSetPrimary}
        onAddRegion={handleAddRegion}
        onDeleteRegion={handleDeleteRegion} 
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' }
});