import React, { useState} from 'react';
import { View, StyleSheet, TextInput, ScrollView, TouchableOpacity, ActivityIndicator, Alert, Modal, Pressable, Image } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter, useLocalSearchParams } from 'expo-router';
import { Text } from '../../components/CustomText';
import * as ImagePicker from 'expo-image-picker';
import { usedApi, UsedProductPriceType } from '../../api/used';
import { USED_CATEGORIES } from '../../constants/usedCategories';

export default function UsedTradeWriteScreen() {
  const router = useRouter();
  const params = useLocalSearchParams();
  const currentRegionId = params.regionId ? Number(params.regionId) : null;

  const [isLoading, setIsLoading] = useState(false);
  const [images, setImages] = useState<string[]>([]);

  const [title, setTitle] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [price, setPrice] = useState('');
  const [isFree, setIsFree] = useState(false);
  const [description, setDescription] = useState('');
  const [tradeLocation, setTradeLocation] = useState(''); 

  const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false);

  // price는 숫자만 담고, 화면에는 콤마를 찍어서 보여준다. 전송할 때 되돌릴 필요가 없다.
  const handlePriceChange = (text: string) => {
    setPrice(text.replace(/[^0-9]/g, ''));
  };
  const formattedPrice = price ? Number(price).toLocaleString() : '';

  const selectableCategories = USED_CATEGORIES.filter(c => c.id !== null);
  const selectedCategoryName = selectableCategories.find(c => c.id === categoryId)?.name || '카테고리를 선택해주세요';

  const pickImages = async () => {
    if (images.length >= 10) {
      Alert.alert('알림', '사진은 최대 10장까지 등록 가능합니다.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsMultipleSelection: true,
      selectionLimit: 10 - images.length,
      quality: 0.8,
    });

    if (!result.canceled) {
      const newUris = result.assets.map(asset => asset.uri);
      setImages(prev => [...prev, ...newUris].slice(0, 10)); 
    }
  };

  const removeImage = (index: number) => {
    setImages(prev => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = async () => {
    if (!currentRegionId) {
      Alert.alert('알림', '동네 정보를 찾을 수 없습니다.');
      return;
    }

    if (!title || !categoryId || (!isFree && !price) || !description) {
      Alert.alert('알림', '모든 항목을 입력해주세요.');
      return;
    }

    setIsLoading(true);
    try {
      const payload = {
        categoryId: categoryId,
        regionId: currentRegionId,
        title: title,
        content: description, 
        priceType: isFree ? 'FREE' : 'FIXED' as UsedProductPriceType,
        price: isFree ? 0 : Number(price)
      };

      const res = await usedApi.createUsedProduct(payload);
      const newProductId = res.data?.data?.usedProductId || res.data?.usedProductId; 

      //  JSON 배열 형태로 이미지 URL 전송
      if (images.length > 0 && newProductId) {
        // 주의: 실제 환경에서는 이미지를 S3 등에 먼저 업로드하고 발급받은 URL을 사용해야 합니다.
        // 현재는 기기 내부 URI를 임시로 넘깁니다.
        const imagePayload = {
          images: images.map(uri => ({ imageUrl: uri }))
        };

        await usedApi.uploadImages(newProductId, imagePayload);
      }

      Alert.alert('성공', '게시글이 등록되었습니다.', [
        { 
          text: '확인', 
          onPress: () => {
            if (router.canGoBack()) {
              router.back(); 
            } else {
              router.replace('/'); 
            }
          } 
        }
      ]);
    } catch (error) {
      console.error('글 작성 실패:', error);
      Alert.alert('오류', '게시글 등록에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Stack.Screen options={{ headerShown: false }} />
      
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIcon}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>판매글 작성</Text>
        <TouchableOpacity onPress={handleSubmit} disabled={isLoading} style={styles.headerSubmit}>
          {isLoading ? <ActivityIndicator size="small" color="#00A859" /> : <Text style={styles.headerSubmitText}>완료</Text>}
        </TouchableOpacity>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        <View style={styles.section}>
          <Text style={styles.label}>사진 <Text style={styles.labelSub}>{images.length}/10</Text></Text>
          
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.photoScroll}>
            <TouchableOpacity style={styles.photoButton} onPress={pickImages}>
              <Ionicons name="add" size={32} color="#999" />
            </TouchableOpacity>

            {images.map((uri, index) => (
              <View key={index} style={styles.previewContainer}>
                <Image source={{ uri }} style={styles.previewImage} />
                {index === 0 && (
                  <View style={styles.representativeBadge}>
                    <Text style={styles.representativeText}>대표</Text>
                  </View>
                )}
                <TouchableOpacity style={styles.removeButton} onPress={() => removeImage(index)}>
                  <Ionicons name="close" size={14} color="#FFF" />
                </TouchableOpacity>
              </View>
            ))}
          </ScrollView>

          <Text style={styles.helperText}>첫번째 사진이 대표사진이 됩니다.</Text>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text style={styles.label}>제목</Text>
          <View style={styles.inputWrapper}>
            <TextInput
              style={styles.input}
              placeholder="상품 제목을 입력해주세요"
              placeholderTextColor="#999"
              value={title}
              onChangeText={setTitle}
              maxLength={50}
            />
            <Text style={styles.charCount}>{title.length}/50</Text>
          </View>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text style={styles.label}>카테고리</Text>
          <TouchableOpacity 
            style={[styles.inputWrapper, { paddingVertical: 14 }]} 
            onPress={() => setIsCategoryModalOpen(true)}
          >
            <Text style={[styles.inputText, !categoryId && styles.placeholderText]}>
              {selectedCategoryName}
            </Text>
            <Ionicons name="chevron-down" size={20} color="#999" />
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text style={styles.label}>가격</Text>
          <View style={styles.priceRow}>
            <View style={[styles.inputWrapper, styles.priceInputWrapper, isFree && styles.disabledInput]}>
              <TextInput
                style={[styles.input, isFree && styles.disabledText]}
                placeholder="0"
                placeholderTextColor="#999"
                keyboardType="numeric"
                value={isFree ? '0' : formattedPrice}
                onChangeText={handlePriceChange}
                editable={!isFree}
              />
              <Text style={[styles.currencyText, isFree && styles.disabledText]}>원</Text>
            </View>
            <TouchableOpacity style={styles.freeToggle} onPress={() => setIsFree(!isFree)}>
              <View style={[styles.checkbox, isFree && styles.checkboxActive]}>
                {isFree && <Ionicons name="checkmark" size={14} color="#FFF" />}
              </View>
              <Text style={styles.freeText}>나눔하기</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text style={styles.label}>상품 설명</Text>
          <View style={[styles.inputWrapper, styles.textAreaWrapper]}>
            <TextInput
              style={styles.textArea}
              placeholder="상품에 대한 자세한 설명을 작성해주세요"
              placeholderTextColor="#999"
              multiline
              textAlignVertical="top"
              value={description}
              onChangeText={setDescription}
              maxLength={1000}
            />
            <Text style={styles.charCountBottom}>{description.length}/1000</Text>
          </View>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text style={styles.label}>거래 방법</Text>
          <View style={styles.inputWrapper}>
            <TextInput
              style={styles.input}
              placeholder="거래 희망 장소를 입력해주세요"
              placeholderTextColor="#999"
              value={tradeLocation}
              onChangeText={setTradeLocation}
            />
          </View>
        </View>

        <View style={styles.tipContainer}>
          <Text style={styles.tipTitle}>안전한 거래를 위한 팁</Text>
          <Text style={styles.tipText}>1. 직거래는 공공장소에서 만나주세요.</Text>
          <Text style={styles.tipText}>2. 상품을 직접 확인한 후 거래해주세요.</Text>
          <Text style={styles.tipText}>3. 계좌이체는 신중하게 진행해주세요.</Text>
        </View>

      </ScrollView>

      <Modal visible={isCategoryModalOpen} transparent animationType="fade" onRequestClose={() => setIsCategoryModalOpen(false)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setIsCategoryModalOpen(false)}>
          <View style={styles.modalSheet}>
            {selectableCategories.map(cat => (
              <TouchableOpacity
                key={cat.id}
                style={styles.modalOption}
                onPress={() => {
                  setCategoryId(cat.id);
                  setIsCategoryModalOpen(false);
                }}
              >
                <Text style={[styles.modalOptionText, categoryId === cat.id && styles.modalOptionTextActive]}>{cat.name}</Text>
                {categoryId === cat.id && <Ionicons name="checkmark" size={20} color="#00A859" />}
              </TouchableOpacity>
            ))}
          </View>
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, height: 56, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  headerIcon: { width: 40, alignItems: 'flex-start' },
  headerTitle: { fontSize: 16, fontWeight: 'bold', color: '#333' },
  headerSubmit: { width: 40, alignItems: 'flex-end' },
  headerSubmitText: { fontSize: 15, fontWeight: 'bold', color: '#00A859' },
  scrollContent: { paddingBottom: 40 },
  section: { paddingHorizontal: 20, paddingVertical: 20 },
  divider: { height: 1, backgroundColor: '#F0F0F0', marginHorizontal: 20 },
  label: { fontSize: 14, fontWeight: 'bold', color: '#333', marginBottom: 12 },
  labelSub: { color: '#999', fontWeight: 'normal' },
  photoScroll: { flexDirection: 'row', marginBottom: 8 },
  photoButton: { width: 80, height: 80, backgroundColor: '#F5F5F5', borderRadius: 8, justifyContent: 'center', alignItems: 'center', marginRight: 10 },
  previewContainer: { width: 80, height: 80, borderRadius: 8, marginRight: 10, position: 'relative' },
  previewImage: { width: '100%', height: '100%', borderRadius: 8 },
  removeButton: { position: 'absolute', top: 4, right: 4, backgroundColor: 'rgba(0,0,0,0.6)', width: 20, height: 20, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
  representativeBadge: { position: 'absolute', bottom: 0, left: 0, right: 0, backgroundColor: 'rgba(0, 168, 89, 0.8)', paddingVertical: 4, borderBottomLeftRadius: 8, borderBottomRightRadius: 8, alignItems: 'center' },
  representativeText: { color: '#FFF', fontSize: 10, fontWeight: 'bold' },
  helperText: { fontSize: 12, color: '#999' },
  inputWrapper: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#F5F5F5', borderRadius: 8, paddingHorizontal: 14 },
  input: { flex: 1, paddingVertical: 14, fontSize: 14, color: '#333' },
  inputText: { flex: 1, fontSize: 14, color: '#333' },
  placeholderText: { color: '#999' },
  charCount: { fontSize: 12, color: '#999', marginLeft: 8 },
  priceRow: { flexDirection: 'row', alignItems: 'center' },
  priceInputWrapper: { flex: 1 },
  currencyText: { fontSize: 14, color: '#333', marginLeft: 8 },
  freeToggle: { flexDirection: 'row', alignItems: 'center', marginLeft: 16 },
  checkbox: { width: 20, height: 20, borderRadius: 4, borderWidth: 1, borderColor: '#DDD', backgroundColor: '#FFF', justifyContent: 'center', alignItems: 'center', marginRight: 8 },
  checkboxActive: { backgroundColor: '#00A859', borderColor: '#00A859' },
  freeText: { fontSize: 14, color: '#666' },
  disabledInput: { backgroundColor: '#EAEAEA' },
  disabledText: { color: '#999' },
  textAreaWrapper: { paddingVertical: 14, alignItems: 'flex-start' },
  textArea: { flex: 1, height: 150, fontSize: 14, color: '#333', width: '100%' },
  charCountBottom: { alignSelf: 'flex-end', fontSize: 12, color: '#999', marginTop: 8 },
  tipContainer: { backgroundColor: '#F5F5F5', borderRadius: 8, padding: 16, marginHorizontal: 20, marginTop: 10 },
  tipTitle: { fontSize: 13, fontWeight: 'bold', color: '#333', marginBottom: 10 },
  tipText: { fontSize: 12, color: '#666', marginBottom: 4 },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  modalSheet: { backgroundColor: '#FFF', borderTopLeftRadius: 16, borderTopRightRadius: 16, paddingVertical: 10, paddingBottom: 30 },
  modalOption: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 16, paddingHorizontal: 24 },
  modalOptionText: { fontSize: 15, color: '#333' },
  modalOptionTextActive: { color: '#00A859', fontWeight: 'bold' },
});