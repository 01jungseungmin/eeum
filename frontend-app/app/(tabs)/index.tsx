import React, { useState, useEffect } from 'react';
import { StyleSheet, Alert } from 'react-native';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

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

  // =====================================================================
  // 1. 지역 목록 불러오기
  // =====================================================================
  const loadRegions = async () => {
    /*
      ==================================================
      🚀 [실제 배포용 API 코드] 
      나중에 서버를 연동할 때는 이 주석을 풀고, 아래의 [테스트용 더미 코드]를 지우세요!
      처음 가입한 유저는 데이터가 없으므로 자동으로 빈 모달이 나오게 됩니다.
      ==================================================
    */
    /*
    try {
      const res = await regionApi.getMyRegions();
      // 데이터가 없으면 빈 배열 [] 처리하여 모달을 비웁니다.
      const data = (res.success && res.data) ? res.data : []; 
      setRegions(data);
      
      const primary = data.find((r: any) => r.isPrimary);
      setPrimaryRegionName(primary ? (primary.dong || primary.fullName) : '동네 설정 필요');
    } catch (e) {
      console.log("지역 목록 로딩 실패:", e);
      setRegions([]); // 에러 시 빈 모달
      setPrimaryRegionName('동네 설정 필요');
    }
    */

    /*
      ==================================================
      🛠️ [현재 테스트용 더미 코드] 
      지금 화면이 잘 동작하는지 확인하기 위해 '청운동'을 억지로 넣어둔 코드입니다.
      ==================================================
    */
    const dummyData = [
      { accountRegionId: 1, isPrimary: true, dong: '청운동', verified: true },
      { accountRegionId: 2, isPrimary: false, dong: '효자동', verified: false }
    ];
    setRegions(dummyData);
    const primary = dummyData.find((r: any) => r.isPrimary);
    setPrimaryRegionName(primary ? primary.dong : '동네 설정 필요');
  };

  // =====================================================================
  // 2. 대표 지역 설정하기 (모달에서 동네 클릭 시)
  // =====================================================================
  const handleSetPrimary = async (id: number) => {
    /*
      ==================================================
      🛠️ [현재 테스트용 더미 코드] 
      서버를 거치지 않고 화면상에서 즉각적으로 초록색 체크가 옮겨가고 헤더가 바뀌는 로직입니다.
      ==================================================
    */
    const updatedRegions = regions.map(region => ({
      ...region,
      isPrimary: region.accountRegionId === id // 클릭한 녀석만 true로 변경!
    }));
    setRegions(updatedRegions);
    
    // 바뀐 데이터에서 대표 동네를 찾아 헤더 글자를 즉시 변경!
    const newPrimary = updatedRegions.find(r => r.isPrimary);
    setPrimaryRegionName(newPrimary ? newPrimary.dong : '동네 설정 필요');

    /*
      ==================================================
      🚀 [실제 배포용 API 코드] 
      나중에 위 테스트 코드를 지우고 아래 주석을 푸세요!
      ==================================================
    */
    /*
    try {
      await regionApi.setPrimaryRegion(id);
      loadRegions(); // 서버에 변경 요청 후, 최신 데이터를 다시 불러와서 헤더 갱신
    } catch (e) {
      Alert.alert("오류", "대표 지역 설정에 실패했습니다.");
    }
    */
  };

  // 3. 지역 삭제하기
  const handleDeleteRegion = (id: number) => {
    Alert.alert("삭제", "이 동네를 삭제하시겠습니까?", [
      { text: "취소", style: "cancel" },
      {
        text: "삭제",
        style: "destructive",
        onPress: async () => {
          // 🛠️ 테스트용 프론트 삭제 로직 (배포 시 지우고 아래 API 주석 해제)
          const remainingRegions = regions.filter(r => r.accountRegionId !== id);
          setRegions(remainingRegions);
          const newPrimary = remainingRegions.find(r => r.isPrimary);
          setPrimaryRegionName(newPrimary ? newPrimary.dong : '동네 설정 필요');
          
          /* 🚀 [실제 배포용 API 코드]
          try {
            await regionApi.deleteRegion(id);
            loadRegions(); 
          } catch (e) {
            Alert.alert("오류", "삭제에 실패했습니다.");
          }
          */
        }
      }
    ]);
  };

  const handleAddRegion = () => {
    if (regions.length >= 2) {
      Alert.alert("동네 추가 불가", "동네는 최대 2개까지만 등록할 수 있어요.\n새로운 동네를 추가하려면 기존 동네를 삭제해 주세요.");
      return;
    }
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
        onDeleteRegion={handleDeleteRegion} 
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' }
});