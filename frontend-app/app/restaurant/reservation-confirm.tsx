// import React, { useState, useEffect } from 'react';
// import { View, StyleSheet, ScrollView, TouchableOpacity, Image, Alert } from 'react-native';
// import { Text } from '../../components/CustomText';
// import { SafeAreaView } from 'react-native-safe-area-context';
// import { useRouter, useLocalSearchParams } from 'expo-router';
// import { Ionicons } from '@expo/vector-icons';

// import { shopApi } from '../../api/shop';
// // import { userApi } from '../../api/user'; // ✨ 유저 API import (경로 확인 필요)

// export default function ReservationConfirmScreen() {
//   const router = useRouter();
  
//   // ✨ 동적으로 넘겨받은 파라미터 (month 포함)
//   const { storeId, month, date, time, people, request } = useLocalSearchParams();
  
//   const [shopInfo, setShopInfo] = useState<any>(null);
  
//   // ✨ 실제 유저 데이터를 담을 State
//   const [userInfo, setUserInfo] = useState({
//     name: '로딩중...',
//     phone: '로딩중...'
//   });

//   useEffect(() => {
//     // 1. 상점 정보 불러오기
//     const fetchShop = async () => {
//       try {
//         const data = await shopApi.getShopDetail(Number(storeId));
//         setShopInfo(data);
//       } catch (e) {
//         console.error(e);
//       }
//     };
    
//     // 2. 로그인 유저 정보 불러오기 (실제 데이터)
//     const fetchUserInfo = async () => {
//       try {
//         const myInfo = await userApi.getMyInfo();
//         setUserInfo({
//           name: myInfo.name,
//           phone: myInfo.phone || '전화번호 없음'
//         });
//       } catch (error) {
//         console.error("내 정보 불러오기 실패", error);
//       }
//     };

//     if (storeId) fetchShop();
//     fetchUserInfo();
//   }, [storeId]);

//   const handleFinalReserve = () => {
//     // 백엔드 예약 API 호출 로직이 들어갈 곳
//     router.push('/restaurant/reservation-success' as any);
//   };

//   return (
//     <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
//       <View style={styles.header}>
//         <TouchableOpacity onPress={() => router.back()} style={{ padding: 5 }}>
//           <Ionicons name="chevron-back" size={24} color="#fff" />
//         </TouchableOpacity>
//         <Text fontWeight="bold" style={styles.headerTitle}>예약</Text>
//         <View style={{ width: 34 }} />
//       </View>

//       <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
//         <View style={styles.section}>
//           <Text style={styles.sectionTitle}>예약자</Text>
//           <View style={styles.card}>
//             <View style={styles.row}>
//               <Text fontWeight="bold" style={styles.label}>예약자</Text>
//               {/* ✨ 실제 유저 데이터 렌더링 */}
//               <Text style={styles.value}>{userInfo.name}  {userInfo.phone}</Text>
//             </View>
//             <View style={[styles.row, { marginTop: 10 }]}>
//               <Text fontWeight="bold" style={styles.label}>예약 일정</Text>
//               {/* ✨ 동적으로 전달받은 월/일/시간 렌더링 */}
//               <Text style={styles.value}>{month}월 {date}일 {time}</Text>
//             </View>
//           </View>
//         </View>

//         <View style={styles.section}>
//           <Text style={styles.sectionTitle}>예약 장소</Text>
//           <View style={styles.card}>
//             <Text fontWeight="bold" style={styles.shopName}>{shopInfo?.name || '상점 이름'}</Text>
//             <View style={styles.shopInfoBox}>
//               <Image 
//                 source={{ uri: shopInfo?.images?.[0]?.imageUrl || 'https://via.placeholder.com/100' }} 
//                 style={styles.shopImage} 
//               />
//               <View style={styles.shopDetails}>
//                 <Text style={styles.shopText} numberOfLines={1}>{shopInfo?.address || '주소 정보'}</Text>
//                 <Text style={styles.shopText}>예약 일정  {month}월 {date}일 {time}</Text>
//                 <Text style={styles.shopText}>예약 인원  {people}명</Text>
//               </View>
//             </View>
//           </View>
//         </View>

//         <View style={styles.noticeBox}>
//           <Text style={styles.noticeTitle}>예약 주의사항</Text>
//           <Text style={styles.noticeText}>
//             • 예약 시간과 날짜를 반드시 확인하고 변경 시 미리 연락하세요.{'\n'}
//             • 취소 및 환불 규정을 사전에 확인해 불이익을 방지하세요.{'\n'}
//             • 노쇼 방지를 위해 예약 시간에 맞춰 방문해주세요.
//           </Text>
//         </View>
//       </ScrollView>

//       <View style={styles.bottomBar}>
//         <TouchableOpacity style={styles.submitBtn} onPress={handleFinalReserve}>
//           <Text fontWeight="bold" style={styles.submitBtnText}>예약하기</Text>
//         </TouchableOpacity>
//       </View>
//     </SafeAreaView>
//   );
// }

// const styles = StyleSheet.create({
//   container: { flex: 1, backgroundColor: '#F8F9FA' },
//   header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#00A859' },
//   headerTitle: { fontSize: 16, color: '#fff' },
//   scrollContent: { padding: 20, paddingBottom: 100 },
//   section: { marginBottom: 25 },
//   sectionTitle: { fontSize: 14, color: '#666', marginBottom: 10 },
//   card: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#EBEBEB', borderRadius: 8, padding: 20 },
//   row: { flexDirection: 'row', alignItems: 'center' },
//   label: { width: 70, fontSize: 14, color: '#333' },
//   value: { fontSize: 14, color: '#333', flex: 1 },
//   shopName: { fontSize: 16, color: '#333', marginBottom: 15 },
//   shopInfoBox: { flexDirection: 'row' },
//   shopImage: { width: 70, height: 70, borderRadius: 4, backgroundColor: '#eee', marginRight: 15 },
//   shopDetails: { flex: 1, justifyContent: 'space-around' },
//   shopText: { fontSize: 13, color: '#666' },
//   noticeBox: { backgroundColor: '#F4F5F7', padding: 20, borderRadius: 8 },
//   noticeTitle: { fontSize: 13, color: '#888', marginBottom: 8, fontWeight: 'bold' },
//   noticeText: { fontSize: 12, color: '#888', lineHeight: 20 },
//   bottomBar: { position: 'absolute', bottom: 0, width: '100%', backgroundColor: '#F8F9FA', padding: 20, paddingTop: 10 },
//   submitBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
//   submitBtnText: { color: '#fff', fontSize: 16 }
// });