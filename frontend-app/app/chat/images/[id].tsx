import React, { useCallback, useEffect, useState } from 'react';
import { View, StyleSheet, FlatList, Image, TouchableOpacity, ActivityIndicator, Modal, useWindowDimensions } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { Text } from '../../../components/CustomText';
import { chatApi } from '../../../api/chat';

interface ChatImage {
  messageId: number;
  senderAccountId: number;
  senderName: string | null;
  imageUrl: string;
  sentAt: string;
}

const COLUMNS = 3;
const GAP = 2;

/** 채팅방에서 주고받은 사진만 모아 본다. 삭제된 메시지의 사진은 서버가 빼고 준다. */
export default function ChatRoomImagesScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();
  const roomId = Number(id);

  // 웹 데모는 폰 틀 너비로 보정된 값이 들어온다. 모듈 로드 시점에 한 번 재면 안 된다.
  const { width } = useWindowDimensions();
  const cellSize = Math.floor((width - GAP * (COLUMNS - 1)) / COLUMNS);

  const [images, setImages] = useState<ChatImage[]>([]);
  const [cursor, setCursor] = useState<{ value: string | null; id: number | null }>({ value: null, id: null });
  const [hasNext, setHasNext] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);

  useEffect(() => {
    if (!roomId || Number.isNaN(roomId)) return;

    let cancelled = false;

    chatApi.getRoomImages(roomId)
      .then(slice => {
        if (cancelled) return;
        setImages(slice.content);
        setHasNext(slice.hasNext);
        setCursor({ value: slice.nextCursorValue, id: slice.nextCursorId });
      })
      .finally(() => { if (!cancelled) setIsLoading(false); });

    return () => { cancelled = true; };
  }, [roomId]);

  const loadMore = useCallback(async () => {
    if (!hasNext || isLoadingMore) return;

    setIsLoadingMore(true);
    try {
      const slice = await chatApi.getRoomImages(roomId, cursor.value, cursor.id);
      setImages(prev => [...prev, ...slice.content]);
      setHasNext(slice.hasNext);
      setCursor({ value: slice.nextCursorValue, id: slice.nextCursorId });
    } finally {
      setIsLoadingMore(false);
    }
  }, [hasNext, isLoadingMore, roomId, cursor]);

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Stack.Screen options={{ headerShown: false }} />

      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIcon}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>사진 모아보기</Text>
        <View style={{ width: 42 }} />
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>
      ) : (
        <FlatList
          data={images}
          keyExtractor={(item) => String(item.messageId)}
          numColumns={COLUMNS}
          onEndReached={loadMore}
          onEndReachedThreshold={0.4}
          columnWrapperStyle={{ gap: GAP }}
          contentContainerStyle={{ gap: GAP }}
          renderItem={({ item }) => (
            <TouchableOpacity onPress={() => setPreview(item.imageUrl)}>
              <Image
                source={{ uri: item.imageUrl }}
                style={{ width: cellSize, height: cellSize, backgroundColor: '#F2F2F2' }}
              />
            </TouchableOpacity>
          )}
          ListEmptyComponent={
            <View style={styles.center}>
              <Ionicons name="images-outline" size={40} color="#DDD" style={{ marginBottom: 12 }} />
              <Text style={styles.emptyText}>주고받은 사진이 없어요.</Text>
            </View>
          }
          ListFooterComponent={
            isLoadingMore ? <ActivityIndicator style={{ marginVertical: 20 }} color="#00A859" /> : null
          }
        />
      )}

      <Modal visible={preview !== null} transparent animationType="fade" onRequestClose={() => setPreview(null)}>
        <View style={styles.previewBackdrop}>
          <TouchableOpacity style={styles.previewClose} onPress={() => setPreview(null)}>
            <Ionicons name="close" size={28} color="#FFF" />
          </TouchableOpacity>
          {preview && (
            <Image source={{ uri: preview }} style={styles.previewImage} resizeMode="contain" />
          )}
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 10, height: 56, borderBottomWidth: 1, borderBottomColor: '#EAEAEA' },
  headerIcon: { padding: 8 },
  headerTitle: { fontSize: 17, color: '#333' },
  center: { alignItems: 'center', justifyContent: 'center', paddingVertical: 80 },
  emptyText: { fontSize: 15, color: '#999' },
  previewBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.92)', justifyContent: 'center' },
  previewClose: { position: 'absolute', top: 40, right: 20, zIndex: 10, padding: 8 },
  previewImage: { width: '100%', height: '80%' },
});
