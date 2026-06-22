import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, TextInput, Alert, ScrollView, ActivityIndicator, Image, KeyboardAvoidingView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import * as ImagePicker from 'expo-image-picker'; 
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

// 백엔드 명세와 맞춘 카테고리 ID 매핑
const CATEGORY_MAP = [
  { id: 8, name: '자유게시판' },
  { id: 9, name: '동네소식' },
  { id: 10, name: '분실물' },
  { id: 11, name: '도움요청' },
  { id: 12, name: '공동배달' },
];

export default function CommunityWriteScreen() {
  const router = useRouter();
  const { editId } = useLocalSearchParams(); 
  
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedImages, setSelectedImages] = useState<string[]>([]);

  // 화면 진입 시 기존 데이터 불러오기 (수정 모드)
  useEffect(() => {
    if (editId) {
      const fetchExistingPost = async () => {
        try {
          const data = await communityApi.getPostDetail(editId as string);
          setTitle(data.title);     
          setContent(data.content); 
          if (data.categoryId) setSelectedCategory(data.categoryId);
          if (data.imageUrls && data.imageUrls.length > 0) {
            const existingUrls = data.imageUrls.map((img: any) => img.imageUrl || img);
            setSelectedImages(existingUrls);
          }
        } catch (error) {
          console.error('기존 글 불러오기 실패:', error);
        }
      };
      fetchExistingPost();
    }
  }, [editId]);

  // 갤러리 열어서 사진 고르는 함수
  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('권한 필요', '사진첩 접근 권한이 필요합니다.');
      return;
    }

    let result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsMultipleSelection: true,
      selectionLimit: 5 - selectedImages.length, 
      quality: 0.8,
    });

    if (!result.canceled) {
      const newImages = result.assets.map(asset => asset.uri);
      setSelectedImages((prev) => [...prev, ...newImages].slice(0, 5));
    }
  };

  const removeImage = (indexToRemove: number) => {
    setSelectedImages((prev) => prev.filter((_, index) => index !== indexToRemove));
  };

  const handleSubmit = async () => {
    if (!selectedCategory) return Alert.alert('알림', '카테고리를 선택해주세요.');
    if (!title.trim()) return Alert.alert('알림', '제목을 입력해주세요.');
    if (!content.trim()) return Alert.alert('알림', '내용을 입력해주세요.');

    try {
      setIsSubmitting(true);
      
      const postData = {
        categoryId: selectedCategory,
        title: title,
        content: content,
        imageUrls: [] // 추후 이미지 업로드 API 연동 후 수정 필요
      };

      console.log('🚀 서버로 보내는 데이터:', postData);

      if (editId) {
        await communityApi.updatePost(editId as string, postData);
        Alert.alert('성공', '게시글이 수정되었습니다.', [{ text: '확인', onPress: () => router.back() }]);
      } else {
        await communityApi.createPost(postData);
        Alert.alert('성공', '게시글이 등록되었습니다.', [{ text: '확인', onPress: () => router.back() }]);
      }
    } catch (error: any) {
      console.error('작성/수정 에러:', error);
      Alert.alert('오류', '작성에 실패했습니다. 서버 로그를 확인해주세요.');
    } finally {
      setIsSubmitting(false);
    }
  };

  // 폼이 다 채워졌는지 확인 (작성 완료 버튼 활성화 용도)
  const isFormValid = selectedCategory !== null && title.trim().length > 0 && content.trim().length > 0;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIconBtn}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>
          {editId ? '글 수정' : '글쓰기'}
        </Text>
        <TouchableOpacity onPress={handleSubmit} disabled={isSubmitting || !isFormValid} style={styles.headerIconBtn}>
          {isSubmitting ? (
            <ActivityIndicator size="small" color="#1B854A" />
          ) : (
            <Text fontWeight="bold" style={[styles.submitTextTop, isFormValid ? { color: '#1B854A' } : { color: '#999' }]}>
              완료
            </Text>
          )}
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
          
          {/* ✨ 카테고리 섹션 (드래그 가능한 가로 스크롤 적용) ✨ */}
          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>카테고리</Text>
            {/* View 대신 가로 방향 ScrollView 사용 */}
            <ScrollView 
              horizontal 
              showsHorizontalScrollIndicator={false} 
              contentContainerStyle={styles.categoryScroll}
            >
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

          {/* 제목 섹션 */}
          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>제목</Text>
            <TextInput
              style={styles.inputBox}
              placeholder="제목을 입력하세요"
              placeholderTextColor="#999"
              maxLength={50}
              value={title}
              onChangeText={setTitle}
            />
          </View>

          {/* 내용 섹션 */}
          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>내용</Text>
            <TextInput
              style={[styles.inputBox, styles.contentInputBox]}
              placeholder="내용을 입력하세요"
              placeholderTextColor="#999"
              multiline
              textAlignVertical="top"
              value={content}
              onChangeText={setContent}
            />
          </View>

          {/* 사진 첨부 섹션 */}
          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>사진 ({selectedImages.length}/5)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.photoScroll}>
              <TouchableOpacity style={styles.photoAddBtn} onPress={pickImage} disabled={selectedImages.length >= 5}>
                <Ionicons name="add" size={32} color="#999" />
              </TouchableOpacity>
              {selectedImages.map((uri, index) => (
                <View key={index} style={styles.imagePreviewWrapper}>
                  <Image source={{ uri }} style={styles.imagePreview} />
                  <TouchableOpacity style={styles.removeImageBtn} onPress={() => removeImage(index)}>
                    <Ionicons name="close" size={16} color="#FFF" />
                  </TouchableOpacity>
                </View>
              ))}
            </ScrollView>
          </View>

          {/* 게시글 작성 안내 영역 */}
          <View style={styles.guideBox}>
            <Text fontWeight="bold" style={styles.guideTitle}>게시글 작성 안내</Text>
            <Text style={styles.guideText}>• 욕설, 비방, 허위 사실은 삭제될 수 있습니다.</Text>
            <Text style={styles.guideText}>• 상업적 광고는 금지됩니다.</Text>
            <Text style={styles.guideText}>• 개인정보를 공개하지 마세요.</Text>
          </View>

        </ScrollView>
      </KeyboardAvoidingView>

      {/* 하단 고정 버튼 */}
      <View style={styles.bottomContainer}>
        <TouchableOpacity 
          style={[styles.bottomSubmitBtn, isFormValid ? styles.bottomSubmitBtnActive : null]}
          onPress={handleSubmit}
          disabled={isSubmitting || !isFormValid}
        >
          <Text fontWeight="bold" style={[styles.bottomSubmitBtnText, isFormValid ? { color: '#FFF' } : null]}>
            작성 완료
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  headerIconBtn: { padding: 10, minWidth: 50, alignItems: 'center' },
  headerTitle: { fontSize: 16, color: '#333' },
  submitTextTop: { fontSize: 15 },
  
  scrollContent: { padding: 20, paddingBottom: 40 },
  section: { marginBottom: 24 },
  sectionLabel: { fontSize: 14, color: '#222', marginBottom: 12 },

  // ✨ 가로 스크롤을 위한 카테고리 칩 스타일 수정
  categoryScroll: { paddingRight: 20 }, // 끝까지 스크롤 했을 때 여백을 위해 추가
  categoryChip: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: '#F5F6F8', marginRight: 10 },
  activeCategoryChip: { backgroundColor: '#1B854A' },
  categoryText: { fontSize: 14, color: '#555' },
  activeCategoryText: { color: '#fff', fontWeight: 'bold' },

  inputBox: { borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, paddingHorizontal: 15, paddingVertical: 12, fontSize: 15, color: '#333', backgroundColor: '#FFF' },
  contentInputBox: { minHeight: 180, paddingTop: 15 },

  photoScroll: { flexDirection: 'row' },
  photoAddBtn: { width: 70, height: 70, borderWidth: 1, borderColor: '#CCC', borderStyle: 'dashed', borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginRight: 12, backgroundColor: '#FAFAFA' },
  imagePreviewWrapper: { marginRight: 12, position: 'relative' },
  imagePreview: { width: 70, height: 70, borderRadius: 4, backgroundColor: '#F0F0F0' },
  removeImageBtn: { position: 'absolute', top: -6, right: -6, backgroundColor: 'rgba(0,0,0,0.6)', borderRadius: 12, width: 20, height: 20, justifyContent: 'center', alignItems: 'center' },

  guideBox: { backgroundColor: '#F8F9FA', padding: 20, borderRadius: 8, marginTop: 10 },
  guideTitle: { fontSize: 13, color: '#333', marginBottom: 8 },
  guideText: { fontSize: 13, color: '#777', lineHeight: 22 },

  bottomContainer: { padding: 15, paddingBottom: Platform.OS === 'ios' ? 10 : 20, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#F0F0F0' },
  bottomSubmitBtn: { backgroundColor: '#D4D8DB', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  bottomSubmitBtnActive: { backgroundColor: '#1B854A' },
  bottomSubmitBtnText: { color: '#FFF', fontSize: 16 }
});