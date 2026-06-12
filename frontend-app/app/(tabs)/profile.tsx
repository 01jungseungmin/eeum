import React from 'react';
import { 
  StyleSheet, View, Image, ScrollView, 
  TouchableOpacity, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { client } from '../../api/client';
import { clearTokens, getRefreshToken } from '../../utils/secureStore';
import { Text } from '../../components/CustomText';

export default function ProfileScreen() {
  const router = useRouter();

  // 로그아웃 함수
  const handleLogout = async () => {
    try {
      const refreshToken = await getRefreshToken();
      if (refreshToken) {
        await client.post('/auth/logout', {
          refreshToken: refreshToken
        });
      }
    } catch (error) {
      console.error('서버 로그아웃 통신 에러:', error);
    } finally {
      await clearTokens();
      Alert.alert('알림', '성공적으로 로그아웃 되었습니다.');
      router.replace('/(auth)/login'); 
    }
  };

  // ✨ 클릭 이벤트를 받을 수 있도록 onPress 프롭스 추가
  const MenuItem = ({ icon, title, onPress }: { icon?: string; title: string; onPress?: () => void }) => (
    <TouchableOpacity style={styles.menuItem} onPress={onPress}>
      <View style={styles.menuLeft}>
        {icon && <Ionicons name={icon as any} size={20} color="#555" style={{marginRight: 10}} />}
        <Text style={styles.menuText}>{title}</Text>
      </View>
      <Ionicons name="chevron-forward" size={18} color="#CCC" />
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 상단 탭 버튼 */}
      <View style={styles.topTabContainer}>
        <TouchableOpacity style={[styles.topTab, styles.activeTab]}>
          <Text style={styles.activeTabText}>동네생활</Text>
        </TouchableOpacity>
        
        {/* ✨ 중고거래 탭 주석 처리 */}
        {/* <TouchableOpacity style={styles.topTab}>
          <Text style={styles.inactiveTabText}>중고거래</Text>
        </TouchableOpacity> 
        */}
        
        <View style={styles.topIcons}>
          <Ionicons name="notifications-outline" size={24} color="#333" style={{marginRight: 15}} />
          <Ionicons name="menu-outline" size={28} color="#333" />
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 프로필 카드 */}
        <View style={styles.profileCard}>
          <View style={styles.profileImagePlaceholder}>
            <Ionicons name="person" size={40} color="#EEE" />
          </View>
          <View style={styles.profileInfo}>
            <View style={styles.nameRow}>
              <Text style={styles.userName}>착한사용자</Text>
              <Ionicons name="chevron-forward" size={18} color="#333" />
            </View>
            <View style={styles.locationRow}>
              <Ionicons name="location" size={14} color="#00A859" />
              <Text style={styles.locationText}>부평1동</Text>
            </View>
          </View>
        </View>

        {/* ✨ 메뉴 섹션 - 나의 중고 거래 (전체 주석 처리) */}
        {/* <View style={styles.section}>
          <Text style={styles.sectionTitle}>나의 중고 거래</Text>
          <MenuItem title="찜 목록" />
          <MenuItem title="중고 거래 내역" />
          <MenuItem title="받은 리뷰" />
          <MenuItem title="보낸 리뷰" />
        </View> 
        */}

        {/* 메뉴 섹션 - 나의 동네 상점 거래 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>나의 동네 상점 거래</Text>
          {/* ✨ 라우터 이동 기능 연결 (경로는 추후 생성할 파일 위치로 임시 지정) */}
          <MenuItem 
            title="찜 목록" 
            onPress={() => router.push('/favorites' as any)} 
          />
          <MenuItem 
            title="동네 상점 거래 내역" 
            onPress={() => router.push('/history' as any)} 
          />
          <MenuItem 
            title="작성한 리뷰" 
            onPress={() => Alert.alert('알림', '준비 중인 기능입니다.')}
          />
        </View>

        {/* 메뉴 섹션 - 설정 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>설정</Text>
          <MenuItem title="회원정보 수정" />
          <MenuItem title="결제 수단 관리" />
          <MenuItem title="개인정보 처리방침" />
        </View>

        {/* 메뉴 섹션 - 고객지원 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>고객 지원</Text>
          <MenuItem title="고객센터" />
          <MenuItem title="공지사항" />
        </View>

        {/* 로그아웃 버튼 */}
        <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
          <Ionicons name="log-out-outline" size={20} color="#FF5252" style={{marginRight: 8}} />
          <Text style={styles.logoutText}>로그아웃</Text>
        </TouchableOpacity>

        <Text style={styles.versionText}>버전 v1.0.0</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  topTabContainer: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15, backgroundColor: '#fff' },
  topTab: { paddingVertical: 6, paddingHorizontal: 12, borderRadius: 15, marginRight: 8 },
  activeTab: { backgroundColor: '#00A859' },
  activeTabText: { color: '#fff', fontWeight: 'bold', fontSize: 13 },
  inactiveTabText: { color: '#888', fontSize: 13 },
  topIcons: { flex: 1, flexDirection: 'row', justifyContent: 'flex-end' },

  profileCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#fff', padding: 20, marginBottom: 10 },
  profileImagePlaceholder: { width: 60, height: 60, borderRadius: 30, backgroundColor: '#CCC', justifyContent: 'center', alignItems: 'center' },
  profileInfo: { marginLeft: 15 },
  nameRow: { flexDirection: 'row', alignItems: 'center' },
  userName: { fontSize: 18, fontWeight: 'bold', marginRight: 5 },
  locationRow: { flexDirection: 'row', alignItems: 'center', marginTop: 4 },
  locationText: { fontSize: 13, color: '#666', marginLeft: 3 },

  section: { backgroundColor: '#fff', paddingHorizontal: 20, paddingVertical: 15, marginBottom: 10 },
  sectionTitle: { fontSize: 15, fontWeight: 'bold', color: '#333', marginBottom: 10 },
  menuItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12 },
  menuLeft: { flexDirection: 'row', alignItems: 'center' },
  menuText: { fontSize: 14, color: '#555' },

  logoutButton: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: 20 },
  logoutText: { color: '#FF5252', fontSize: 15, fontWeight: '600' },
  versionText: { textAlign: 'center', color: '#CCC', fontSize: 12, marginBottom: 30 }
});