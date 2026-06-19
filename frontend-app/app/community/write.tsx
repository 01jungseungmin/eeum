import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity, TextInput, Alert, ScrollView, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

// 백엔드 명세와 맞춰야 할 카테고리 ID 매핑 (확인 필요)
const CATEGORY_MAP = [
  { id: 8, name: '자유게시판' },
  { id: 9, name: '동네소식' },
  { id: 10, name: '분실물' },
  { id: 11, name: '도움요청' },
  { id: 12, name: '공동배달' },
];

export default function CommunityWriteScreen() {
  const router = useRouter();
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!selectedCategory) return Alert.alert('알림', '카테고리를 선택해주세요.');
    if (!title.trim()) return Alert.alert('알림', '제목을 입력해주세요.');
    if (!content.trim()) return Alert.alert('알림', '내용을 입력해주세요.');

    try {
      setIsSubmitting(true);
      
      // 명세서 기반 페이로드 전송
      await communityApi.createPost({
        categoryId: selectedCategory,
        title: title,
        content: content,
        imageUrls: [] // 이미지는 나중에 구현
      });

      Alert.alert('성공', '게시글이 등록되었습니다.', [
        { text: '확인', onPress: () => router.back() }
      ]);
    } catch (error: any) {
      console.error('게시글 작성 에러:', error);
      const serverMessage = error.response?.data?.message || '작성에 실패했습니다.';
      Alert.alert('오류', serverMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Ionicons name="close" size={28} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>글 쓰기</Text>
        <TouchableOpacity onPress={handleSubmit} disabled={isSubmitting}>
          {isSubmitting ? (
            <ActivityIndicator size="small" color="#1B854A" />
          ) : (
            <Text fontWeight="bold" style={styles.submitBtnText}>완료</Text>
          )}
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        {/* 카테고리 선택 */}
        <View style={styles.categorySection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>카테고리</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {CATEGORY_MAP.map((cat) => (
              <TouchableOpacity
                key={cat.id}
                style={[styles.categoryChip, selectedCategory === cat.id && styles.activeCategoryChip]}
                onPress={() => setSelectedCategory(cat.id)}
              >
                <Text style={[styles.categoryText, selectedCategory === cat.id && styles.activeCategoryText]}>
                  {cat.name}
                </Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>

        {/* 제목 입력 */}
        <TextInput
          style={styles.titleInput}
          placeholder="제목을 입력하세요 (최대 50자)"
          maxLength={50}
          value={title}
          onChangeText={setTitle}
        />

        {/* 본문 입력 */}
        <TextInput
          style={styles.contentInput}
          placeholder="동네 이웃들과 나누고 싶은 이야기를 적어보세요."
          multiline
          textAlignVertical="top"
          value={content}
          onChangeText={setContent}
        />
      </ScrollView>

      {/* 이미지 첨부 바 (디자인용) */}
      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.imageBtn}>
          <Ionicons name="camera-outline" size={26} color="#666" />
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  headerTitle: { fontSize: 18, color: '#333' },
  submitBtnText: { fontSize: 16, color: '#1B854A' },
  scrollContent: { padding: 20 },
  categorySection: { marginBottom: 20 },
  sectionTitle: { fontSize: 16, color: '#333', marginBottom: 12 },
  categoryChip: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: 20, backgroundColor: '#F5F6F8', marginRight: 8, borderWidth: 1, borderColor: '#EEE' },
  activeCategoryChip: { backgroundColor: '#1B854A', borderColor: '#1B854A' },
  categoryText: { fontSize: 14, color: '#666' },
  activeCategoryText: { color: '#fff', fontWeight: 'bold' },
  titleInput: { fontSize: 18, borderBottomWidth: 1, borderBottomColor: '#EEE', paddingVertical: 15, marginBottom: 15, fontWeight: 'bold' },
  contentInput: { fontSize: 16, lineHeight: 24, minHeight: 200, color: '#333' },
  bottomBar: { padding: 15, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff' },
  imageBtn: { padding: 5 }
});