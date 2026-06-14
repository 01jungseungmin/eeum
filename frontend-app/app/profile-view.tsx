import React, { useState, useEffect } from 'react';
import { 
  View, StyleSheet, TouchableOpacity, Image, 
  ActivityIndicator, Alert, ScrollView 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

// ✨ 경로를 ../ 로 통일했습니다! (app 폴더 바로 아래에 있기 때문)
import { Text } from '../components/CustomText'; 
import { userApi, MyInfoResponse, RegionInfo } from '../api/user';

export default function ProfileViewScreen() {
  const router = useRouter();
  const [userInfo, setUserInfo] = useState<MyInfoResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // 내 정보 조회 API 호출
  useEffect(() => {
    const fetchMyInfo = async () => {
      try {
        const data = await userApi.getMyInfo();
        setUserInfo(data);
      } catch (error) {
        console.error('프로필 로딩 에러:', error);
        Alert.alert('오류', '프로필 정보를 불러오는데 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    };
    fetchMyInfo();
  }, []);

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#1B854A" />
      </View>
    );
  }

  // ✨ 동네 데이터(regions)에서 대표 동네(isPrimary) 추출 로직
  const primaryRegion = userInfo?.regions?.find((r: RegionInfo) => r.isPrimary) || userInfo?.regions?.[0];
  const addressText = primaryRegion 
    ? `${primaryRegion.siDo} ${primaryRegion.gunGu} ${primaryRegion.dong}` 
    : '등록된 동네가 없습니다.';

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIcon}>
          <Ionicons name="chevron-back" size={26} color="#000" />
        </TouchableOpacity>
        
        <Text fontWeight="bold" style={styles.headerTitle}>프로필</Text>
        
        {/* 우측 상단 톱니바퀴 -> 회원정보 수정(/edit)으로 이동 */}
        <TouchableOpacity onPress={() => router.push('/edit' as any)} style={styles.headerIcon}>
          <Ionicons name="settings-outline" size={24} color="#333" />
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        
        {/* 1. 프로필 이미지 및 기본 정보 (API 연동) */}
        <View style={styles.profileSection}>
          {userInfo?.profileImageUrl ? (
            <Image source={{ uri: userInfo.profileImageUrl }} style={styles.profileImage} />
          ) : (
            <View style={styles.placeholderImage}>
              <Ionicons name="person" size={50} color="#CCC" />
            </View>
          )}
          <Text fontWeight="bold" style={styles.nicknameText}>
            {userInfo?.nickname || '이름 없음'}
          </Text>
          <Text style={styles.addressText}>{addressText}</Text>
        </View>

        {/* 2. 활동 통계 영역 (API 연동) */}
        <View style={styles.statsContainer}>
          <View style={styles.statItem}>
            {/* 데이터가 없을 경우 기본값 0 표시 */}
            <Text fontWeight="bold" style={styles.statNumber}>
              {userInfo?.receivedReviewCount ?? 0}
            </Text>
            <Text style={styles.statLabel}>받은 리뷰</Text>
          </View>
          <View style={styles.statDivider} />
          
          <View style={styles.statItem}>
            <Text fontWeight="bold" style={styles.statNumber}>
              {userInfo?.sentReviewCount ?? 0}
            </Text>
            <Text style={styles.statLabel}>보낸 리뷰</Text>
          </View>
          <View style={styles.statDivider} />
          
          <View style={styles.statItem}>
            <Text fontWeight="bold" style={styles.statNumber}>
              {userInfo?.tradeCount ?? 0}
            </Text>
            <Text style={styles.statLabel}>거래 횟수</Text>
          </View>
        </View>

        {/* 3. 자기소개 영역 (API 연동) */}
        <View style={styles.introSection}>
          <Text fontWeight="bold" style={styles.introTitle}>자기소개</Text>
          <View style={styles.introBox}>
            <Text style={styles.introText}>
              {userInfo?.introduction || '등록된 자기소개가 없습니다.'}
            </Text>
          </View>
        </View>

      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  
  header: { 
    flexDirection: 'row', 
    alignItems: 'center', 
    justifyContent: 'space-between',
    paddingHorizontal: 15, 
    paddingVertical: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F0F0'
  },
  headerTitle: { fontSize: 18, color: '#000' },
  headerIcon: { padding: 4 },

  scrollContent: { paddingBottom: 40 },

  profileSection: { alignItems: 'center', marginTop: 30, marginBottom: 25 },
  profileImage: { width: 100, height: 100, borderRadius: 50, marginBottom: 15 },
  placeholderImage: { 
    width: 100, 
    height: 100, 
    borderRadius: 50, 
    backgroundColor: '#F5F5F5', 
    justifyContent: 'center', 
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E0E0E0',
    marginBottom: 15
  },
  nicknameText: { fontSize: 22, color: '#333', marginBottom: 6 },
  addressText: { fontSize: 14, color: '#888' },

  statsContainer: { 
    flexDirection: 'row', 
    backgroundColor: '#FAFAFA', 
    marginHorizontal: 20, 
    paddingVertical: 20, 
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#F0F0F0',
    marginBottom: 30
  },
  statItem: { flex: 1, alignItems: 'center' },
  statNumber: { fontSize: 24, color: '#1B854A', marginBottom: 4 }, // 포인트 초록색
  statLabel: { fontSize: 13, color: '#666' },
  statDivider: { width: 1, backgroundColor: '#EAEAEA', marginVertical: 5 },

  introSection: { paddingHorizontal: 20 },
  introTitle: { fontSize: 16, color: '#333', marginBottom: 12 },
  introBox: { 
    backgroundColor: '#F9F9F9', 
    padding: 20, 
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#F0F0F0'
  },
  introText: { fontSize: 14, color: '#555', lineHeight: 22 }
});