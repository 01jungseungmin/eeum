import React, { useState, useCallback } from 'react';
import { 
  StyleSheet, View, TouchableOpacity, Image, 
  ScrollView, ActivityIndicator, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';

import { Text } from '../../components/CustomText';
import { userApi, MyInfoResponse } from '../../api/user';
import { notificationApi } from '../../api/notification';

import { client } from '../../api/client';
import { getRefreshToken, clearTokens } from '../../utils/secureStore'; 

export default function ProfileScreen() {
  const router = useRouter();
  
  const [userInfo, setUserInfo] = useState<MyInfoResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useFocusEffect(
    useCallback(() => {
      const fetchMyInfo = async () => {
        try {
          const data = await userApi.getMyInfo();
          setUserInfo(data);
        } catch (error) {
          console.error('내 정보 로딩 에러:', error);
        } finally {
          setIsLoading(false);
        }
      };
      fetchMyInfo();
    }, [])
  );

  const handleLogout = async () => {
    try {
      const refreshToken = await getRefreshToken();
      if (refreshToken) {
        await client.post('/auth/logout', {
          refreshToken: refreshToken
        });
      }
      await notificationApi.updateFcmToken(''); 
      console.log('FCM 기기 토큰 서버 등록 해제 완료');
    } catch (error) {
      console.error('서버 로그아웃 통신 에러:', error);
    } finally {
      await clearTokens();
      Alert.alert('알림', '성공적으로 로그아웃 되었습니다.');
      router.replace('/(auth)/login'); 
    }
  };

  const MenuItem = ({ title, iconName, onPress }: { title: string; iconName: keyof typeof Ionicons.glyphMap; onPress: () => void }) => (
    <TouchableOpacity style={styles.menuItem} onPress={onPress}>
      <View style={styles.menuItemLeft}>
        <Ionicons name={iconName} size={20} color="#555" style={styles.menuIcon} />
        <Text style={styles.menuText}>{title}</Text>
      </View>
      <Ionicons name="chevron-forward" size={18} color="#CCC" />
    </TouchableOpacity>
  );

  const SectionHeader = ({ title }: { title: string }) => (
    <View style={styles.sectionHeaderContainer}>
      <Text fontWeight="bold" style={styles.sectionHeaderText}>{title}</Text>
    </View>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>마이페이지</Text>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        
        {/* 프로필 정보 요약 */}
        <View style={styles.profileSection}>
          {isLoading ? (
            <ActivityIndicator size="small" color="#1B854A" style={{ padding: 20 }} />
          ) : (
            <View style={styles.profileInfoRow}>
              {userInfo?.profileImageUrl ? (
                <Image source={{ uri: userInfo.profileImageUrl }} style={styles.profileImage} />
              ) : (
                <View style={styles.placeholderImage}>
                  <Ionicons name="image-outline" size={30} color="#CCC" />
                </View>
              )}
              
              <View style={styles.profileTextContainer}>
                <Text fontWeight="bold" style={styles.nicknameText}>
                  {userInfo?.nickname || '닉네임'}
                </Text>
                <TouchableOpacity onPress={() => router.push('/mypage/profile-view' as any)}>
                  <Text style={styles.profileLinkText}>프로필 보기 {'>'}</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
        </View>

        <View style={styles.divider} />

        {/* 나의 메뉴 리스트들 */}
        <View style={styles.menuSectionContainer}>
          <SectionHeader title="나의 중고 거래" />
          <MenuItem title="찜 목록" iconName="heart-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
          <MenuItem title="중고 거래 내역" iconName="bag-handle-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
          <MenuItem title="받은 리뷰" iconName="star-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
          <MenuItem title="보낸 리뷰" iconName="star-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
        </View>

        <View style={styles.divider} />

        <View style={styles.menuSectionContainer}>
          <SectionHeader title="나의 동네 상점 거래" />
          <MenuItem title="찜 목록" iconName="heart-outline" onPress={() => router.push('/mypage/favorites' as any)} />
          <MenuItem title="동네 상점 구매 내역" iconName="bag-check-outline" onPress={() => router.push('/mypage/history' as any)} />
          <MenuItem title="동네 상점 예약 내역" iconName="calendar-outline" onPress={() => router.push('/mypage/reservations' as any)} />
          <MenuItem title="작성한 리뷰" iconName="document-text-outline" onPress={() => router.push('/mypage/my-reviews' as any)} />
        </View>

        <View style={styles.divider} />

        <View style={styles.menuSectionContainer}>
          <SectionHeader title="나의 커뮤니티 활동" />
          <MenuItem title="작성한 게시글" iconName="document-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
          <MenuItem title="작성한 댓글" iconName="chatbubble-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
        </View>
        
        <View style={styles.divider} />

        <View style={styles.menuSectionContainer}>
          <SectionHeader title="설정" />
          <MenuItem title="회원정보 수정" iconName="settings-outline" onPress={() => router.push('/mypage/edit' as any)} />
          <MenuItem title="비밀번호 변경" iconName="lock-closed-outline" onPress={() => router.push('/mypage/change-password' as any)} />
          <MenuItem title="알림 설정" iconName="notifications-outline" onPress={() => router.push('/mypage/notification-setting' as any)} />
          <MenuItem title="회원탈퇴" iconName="person-remove-outline" onPress={() => router.push('/mypage/withdraw' as any)} />
        </View>

        <View style={styles.divider} />

        <View style={styles.menuSectionContainer}>
          <SectionHeader title="고객 지원" />
          <MenuItem title="고객센터" iconName="help-circle-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
          <MenuItem title="공지사항" iconName="document-text-outline" onPress={() => Alert.alert('알림', '준비 중입니다.')} />
        </View>

        <TouchableOpacity 
          style={styles.logoutButton} 
          onPress={() => Alert.alert(
            '로그아웃', 
            '정말로 로그아웃 하시겠습니까?',
            [
              { text: '취소', style: 'cancel' },
              { text: '확인', style: 'destructive', onPress: handleLogout }
            ]
          )}
        >
          <Ionicons name="log-out-outline" size={20} color="#E74C3C" style={{ marginRight: 6 }} />
          <Text style={styles.logoutText}>로그아웃</Text>
        </TouchableOpacity>

      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  headerTitle: { fontSize: 18, color: '#333' },
  scrollContent: { paddingBottom: 40 },
  profileSection: { paddingHorizontal: 20, paddingVertical: 25, backgroundColor: '#fff' },
  profileInfoRow: { flexDirection: 'row', alignItems: 'center' },
  profileImage: { width: 60, height: 60, borderRadius: 30, marginRight: 15 },
  placeholderImage: { width: 60, height: 60, borderRadius: 30, backgroundColor: '#F5F5F5', justifyContent: 'center', alignItems: 'center', marginRight: 15, borderWidth: 1, borderColor: '#EEE' },
  profileTextContainer: { flex: 1, justifyContent: 'center' },
  nicknameText: { fontSize: 18, color: '#333', marginBottom: 6 },
  profileLinkText: { fontSize: 14, color: '#666' },
  divider: { height: 8, backgroundColor: '#F8F9FA' },
  menuSectionContainer: { paddingVertical: 10 },
  sectionHeaderContainer: { paddingHorizontal: 20, paddingVertical: 12 },
  sectionHeaderText: { fontSize: 14, color: '#333' },
  menuItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 14, paddingHorizontal: 20 },
  menuItemLeft: { flexDirection: 'row', alignItems: 'center' },
  menuIcon: { marginRight: 12 },
  menuText: { fontSize: 15, color: '#333' },
  logoutButton: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', marginTop: 30, marginBottom: 20 },
  logoutText: { fontSize: 15, color: '#E74C3C' }
});