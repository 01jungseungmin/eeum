import React, { useState, useEffect, useRef } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, TextInput, KeyboardAvoidingView, Platform, ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Text } from '../../components/CustomText';
import { chatApi } from '../../api/chat';
import { userApi } from '../../api/user';

export default function ChatRoomScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams();
  const flatListRef = useRef<FlatList>(null);
  
  const [inputText, setInputText] = useState('');
  const [messages, setMessages] = useState<any[]>([]);
  const [myUserId, setMyUserId] = useState<number | string>(''); // 진짜 내 아이디
  const [isLoading, setIsLoading] = useState(true);

  // ✨ 화면 최초 진입 시 데이터 세팅
  useEffect(() => {
    const initChatRoom = async () => {
      try {
        setIsLoading(true);

        // 1. 내 정보 불러오기 (말풍선 렌더링 구분용)
        const myInfo = await userApi.getMyInfo();
        setMyUserId(myInfo?.accountId);

        // 2. 메시지 과거 내역 불러오기
        const msgRes = await chatApi.getMessages(id as string);
        const realMsgs = msgRes?.data?.content || msgRes?.data || msgRes || [];
        setMessages(realMsgs);

        // 3. 채팅방 읽음 처리 API 호출
        await chatApi.markAsRead(id as string);

      } catch (error) {
        console.error('채팅방 정보 로딩 실패', error);
      } finally {
        setIsLoading(false);
      }
    };

    if (id) initChatRoom();
  }, [id]);

  // ✨ 메시지 발송 (Optimistic UI + 에러 필터링 적용)
  const handleSend = async () => {
    if (!inputText.trim()) return;

    // 임시 ID (롤백을 위해 저장)
    const tempId = `temp-${Date.now()}`;
    const textToSend = inputText;

    // 1. 화면에 내 메시지 즉시 띄우기 (성공했다고 가정)
    const newMessage = {
      messageId: tempId,
      senderId: myUserId,
      content: textToSend, // 백엔드 필드명 방어 (content or text)
      text: textToSend,
      createdAt: new Date().toISOString(), 
    };
    
    setMessages((prev) => [...prev, newMessage]);
    setInputText('');

    // 2. 서버에 진짜로 전송하기
    try {
      await chatApi.sendMessage(id as string, textToSend);
      // 추후 여기서 서버가 준 진짜 ID와 시간을 받아 업데이트 할 수 있습니다.
    } catch (error: any) {
      console.error('메시지 전송 에러:', error);
      
      // 🚨 실패 시: 방금 추가했던 가짜 메시지를 화면에서 삭제 (롤백)
      setMessages((prev) => prev.filter((m) => m.messageId !== tempId));
      
      // 🚨 백엔드가 보내준 403 / 400 에러 메시지 띄우기
      const serverMessage = error.response?.data?.message || error.response?.data?.error?.message;
      Alert.alert('전송 실패', serverMessage || '메시지 전송 중 오류가 발생했습니다.');
    }
  };

  const renderMessage = ({ item }: { item: any }) => {
    // 서버 데이터의 senderId가 내 ID와 일치하면 초록색(오른쪽)
    const isMe = String(item.senderId) === String(myUserId);
    const messageText = item.content || item.text || '';
    
    // 시간 텍스트 포맷 (간단한 파싱)
    const timeString = item.createdAt 
      ? new Date(item.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : '';

    return (
      <View style={[styles.messageWrapper, isMe ? styles.messageWrapperMe : styles.messageWrapperOther]}>
        {!isMe && (
          <View style={styles.senderAvatar}>
            <Ionicons name="image-outline" size={20} color="#CCC" />
          </View>
        )}
        <View style={[styles.messageContent, isMe ? styles.alignRight : styles.alignLeft]}>
          {!isMe && <Text style={styles.senderName}>{item.senderName || '상대방'}</Text>}
          <View style={styles.bubbleRow}>
            {isMe && <Text style={styles.messageTime}>{timeString}</Text>}
            <View style={[styles.bubble, isMe ? styles.myBubble : styles.otherBubble]}>
              <Text style={[styles.messageText, isMe ? styles.myMessageText : styles.otherMessageText]}>
                {messageText}
              </Text>
            </View>
            {!isMe && <Text style={styles.messageTime}>{timeString}</Text>}
          </View>
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={28} color="#333" />
        </TouchableOpacity>
        <View style={styles.headerTitleContainer}>
          <Text fontWeight="bold" style={styles.headerTitle}>채팅방</Text>
        </View>
      </View>

      {isLoading ? (
        <View style={styles.centerLoading}>
          <ActivityIndicator size="large" color="#1B854A" />
        </View>
      ) : (
        <FlatList
          ref={flatListRef}
          data={messages}
          keyExtractor={(item) => (item.messageId || item.id || Math.random()).toString()}
          renderItem={renderMessage}
          contentContainerStyle={styles.chatList}
          onContentSizeChange={() => flatListRef.current?.scrollToEnd({ animated: true })}
          onLayout={() => flatListRef.current?.scrollToEnd({ animated: true })}
        />
      )}

      <KeyboardAvoidingView 
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
      >
        <View style={styles.inputContainer}>
          <View style={styles.inputBox}>
            <TextInput
              style={styles.input}
              placeholder="메시지를 입력하세요"
              value={inputText}
              onChangeText={setInputText}
              multiline
            />
            <TouchableOpacity onPress={handleSend} style={styles.sendButton}>
              <Ionicons name="paper-plane" size={20} color="#FFF" />
            </TouchableOpacity>
          </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

// 스타일
const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  centerLoading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#fff', borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backButton: { marginRight: 10 },
  headerTitleContainer: { flex: 1, flexDirection: 'row', alignItems: 'center' },
  headerTitle: { fontSize: 18, color: '#333' },
  chatList: { padding: 20, paddingBottom: 30 },
  messageWrapper: { flexDirection: 'row', marginBottom: 20 },
  messageWrapperMe: { justifyContent: 'flex-end' },
  messageWrapperOther: { justifyContent: 'flex-start' },
  senderAvatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#fff', justifyContent: 'center', alignItems: 'center', marginRight: 10, borderWidth: 1, borderColor: '#EEE' },
  messageContent: { maxWidth: '75%' },
  alignLeft: { alignItems: 'flex-start' },
  alignRight: { alignItems: 'flex-end' },
  senderName: { fontSize: 13, color: '#666', marginBottom: 4, marginLeft: 4 },
  bubbleRow: { flexDirection: 'row', alignItems: 'flex-end' },
  bubble: { paddingHorizontal: 16, paddingVertical: 12, borderRadius: 20 },
  myBubble: { backgroundColor: '#1B854A', borderTopRightRadius: 4 },
  otherBubble: { backgroundColor: '#fff', borderTopLeftRadius: 4, borderWidth: 1, borderColor: '#F0F0F0' },
  messageText: { fontSize: 15, lineHeight: 20 },
  myMessageText: { color: '#fff' },
  otherMessageText: { color: '#333' },
  messageTime: { fontSize: 11, color: '#999', marginHorizontal: 6, marginBottom: 4 },
  inputContainer: { padding: 10, paddingBottom: 20, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#EEE' },
  inputBox: { flexDirection: 'row', alignItems: 'flex-end', backgroundColor: '#F5F6F8', borderRadius: 24, paddingLeft: 15, paddingRight: 5, paddingVertical: 5 },
  input: { flex: 1, maxHeight: 100, minHeight: 40, fontSize: 15, paddingTop: 10, paddingBottom: 10 },
  sendButton: { width: 36, height: 36, borderRadius: 18, backgroundColor: '#CCC', justifyContent: 'center', alignItems: 'center', margin: 4 },
});