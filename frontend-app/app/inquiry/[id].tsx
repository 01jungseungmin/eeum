import React, { useState, useEffect } from 'react';
import { 
  View, StyleSheet, ScrollView, TouchableOpacity, 
  ActivityIndicator, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Text } from '../../components/CustomText'; // 커스텀 텍스트 경로 확인해주세요!
import { inquiryApi } from '../../api/inquiry';

export default function InquiryDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams(); // 선택한 문의의 ID

  const [inquiry, setInquiry] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);

  // 화면이 켜질 때 상세 데이터를 불러옵니다.
  useEffect(() => {
    if (id) {
      fetchInquiryDetail();
    }
  }, [id]);

  const fetchInquiryDetail = async () => {
    try {
      setIsLoading(true);
      const data = await inquiryApi.getInquiryDetail(Number(id));
      setInquiry(data);
    } catch (error) {
      console.error('문의 상세 로딩 실패:', error);
      Alert.alert('오류', '문의 상세 내용을 불러오는데 실패했습니다.', [
        { text: '확인', onPress: () => router.back() }
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const getStatusInfo = (status: string) => {
    if (status === 'ANSWERED' || status === 'COMPLETED') {
      return { text: '답변완료', color: '#00A859', bgColor: '#E8F5E9' };
    }
    return { text: '답변대기', color: '#FF9800', bgColor: '#FFF3E0' };
  };

  if (isLoading || !inquiry) {
    return (
      <SafeAreaView style={styles.centerLoading} edges={['top']}>
        <ActivityIndicator size="large" color="#00A859" />
      </SafeAreaView>
    );
  }

  const statusInfo = getStatusInfo(inquiry.status);
  const dateText = inquiry.createdAt ? inquiry.createdAt.replace('T', ' ').substring(0, 16) : '';

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>문의 상세</Text>
        <View style={{ width: 26 }} /> 
      </View>

      <ScrollView style={styles.contentScroll} showsVerticalScrollIndicator={false}>
        
        {/* 1. 내가 쓴 문의 내역 영역 */}
        <View style={styles.questionSection}>
          <View style={styles.qHeaderRow}>
            <View style={[styles.statusBadge, { backgroundColor: statusInfo.bgColor }]}>
              <Text style={[styles.statusText, { color: statusInfo.color }]}>{statusInfo.text}</Text>
            </View>
            <Text style={styles.dateText}>{dateText}</Text>
          </View>

          <Text style={styles.categoryText}>
            [{inquiry.targetType === 'ADMIN' ? '고객센터' : '상점문의'}] {inquiry.storeName}
          </Text>
          
          <Text fontWeight="bold" style={styles.titleText}>
            {inquiry.secret && <Ionicons name="lock-closed" size={16} color="#888" style={{ marginRight: 6 }} />}
            {inquiry.title}
          </Text>

          <View style={styles.divider} />

          <Text style={styles.contentText}>{inquiry.content}</Text>
        </View>

        {/* 2. 답변 영역 (답변이 있을 때와 없을 때를 분기 처리) */}
        <View style={styles.answerSection}>
          <View style={styles.answerHeader}>
            <Ionicons name="chatbubbles" size={20} color="#00A859" style={{ marginRight: 8 }} />
            <Text fontWeight="bold" style={styles.answerTitle}>답변 내역</Text>
          </View>

          {inquiry.answers && inquiry.answers.length > 0 ? (
            // 답변이 달린 경우
            inquiry.answers.map((answer: any) => (
              <View key={answer.answerId} style={styles.answerBox}>
                <View style={styles.answerWriterRow}>
                  <Text fontWeight="bold" style={styles.answerWriterName}>
                    {answer.writerType === 'OWNER' ? '사장님' : '관리자'} 
                    {answer.writerName ? ` (${answer.writerName})` : ''}
                  </Text>
                  <Text style={styles.answerDate}>
                    {answer.createdAt ? answer.createdAt.replace('T', ' ').substring(0, 16) : ''}
                  </Text>
                </View>
                <Text style={styles.answerContent}>{answer.content}</Text>
              </View>
            ))
          ) : (
            // 답변이 아직 없는 경우
            <View style={styles.emptyAnswerBox}>
              <Ionicons name="time-outline" size={32} color="#CCC" style={{ marginBottom: 10 }} />
              <Text style={styles.emptyAnswerText}>사장님이 확인 후 답변을 남겨드릴 예정입니다.</Text>
              <Text style={styles.emptyAnswerSubText}>조금만 기다려주세요!</Text>
            </View>
          )}
        </View>

      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F6F8' }, // 배경을 살짝 회색으로 깔아서 카드를 돋보이게 함
  centerLoading: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F5F6F8' },
  
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#F0F0F0'
  },
  backButton: { padding: 4 },
  headerTitle: { fontSize: 18, color: '#333' },
  
  contentScroll: { flex: 1, padding: 15 },

  // 질문(내 문의) 영역 카드
  questionSection: { 
    backgroundColor: '#fff', padding: 20, borderRadius: 12, marginBottom: 15,
    shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 3, elevation: 2
  },
  qHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  statusBadge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 6 },
  statusText: { fontSize: 13, fontWeight: 'bold' },
  dateText: { fontSize: 13, color: '#999' },
  categoryText: { fontSize: 14, color: '#666', marginBottom: 8 },
  titleText: { fontSize: 18, color: '#222', lineHeight: 26, marginBottom: 15 },
  divider: { height: 1, backgroundColor: '#F0F0F0', marginBottom: 15 },
  contentText: { fontSize: 15, color: '#444', lineHeight: 24, minHeight: 80 },

  // 답변 영역
  answerSection: { 
    backgroundColor: '#fff', padding: 20, borderRadius: 12, marginBottom: 30,
    shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 3, elevation: 2
  },
  answerHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  answerTitle: { fontSize: 16, color: '#333' },
  
  answerBox: { 
    backgroundColor: '#F8F9FA', padding: 16, borderRadius: 8, marginTop: 5 
  },
  answerWriterRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  answerWriterName: { fontSize: 15, color: '#00A859' }, // 사장님/관리자 이름은 포인트 컬러로 강조
  answerDate: { fontSize: 12, color: '#999' },
  answerContent: { fontSize: 15, color: '#444', lineHeight: 24 },

  emptyAnswerBox: { 
    alignItems: 'center', justifyContent: 'center', paddingVertical: 30, backgroundColor: '#FAFAFA', borderRadius: 8
  },
  emptyAnswerText: { fontSize: 15, color: '#666', fontWeight: 'bold', marginBottom: 6 },
  emptyAnswerSubText: { fontSize: 14, color: '#999' }
});