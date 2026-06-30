import React, { useState, useEffect, useRef } from 'react';
import { 
  View, StyleSheet, TextInput, TouchableOpacity, FlatList, 
  KeyboardAvoidingView, Platform, ActivityIndicator, Image, Alert
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Text } from '../../components/CustomText';

// API 및 커스텀 훅 연동
import { useChatStomp } from '../../hooks/useChat';
import { userApi } from '../../api/user';
import { chatApi } from '../../api/chat';

export default function ChatRoomScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  
  const { id } = useLocalSearchParams();
  const roomId = Number(id);

  // 1. STOMP 훅 불러오기
  const { messages, setMessages, isConnected, sendMessage } = useChatStomp(roomId);

  const [inputText, setInputText] = useState('');
  const [myAccountId, setMyAccountId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  const flatListRef = useRef<FlatList>(null);

  // 2. 내 정보 및 과거 채팅 기록 불러오기
  useEffect(() => {
    const initChatRoom = async () => {
      try {
        setIsLoading(true);
        const myInfo = await userApi.getMyInfo();
        setMyAccountId(myInfo.accountId);

        const pastMessages = await chatApi.getPastMessages(roomId);
        setMessages(pastMessages.reverse()); 
        
      } catch (error: any) {
        console.error('채팅방 초기화 에러:', error);
        
        if (error.response?.status === 403) {
          Alert.alert(
            '입장 불가', 
            '채팅방 참여자가 아닙니다. 먼저 목록에서 참여하기를 눌러주세요.', 
            [{ text: '확인', onPress: () => router.back() }]
          );
        } else {
          Alert.alert('오류', '채팅방 정보를 불러올 수 없습니다.', [{ text: '확인', onPress: () => router.back() }]);
        }
      } finally {
        setIsLoading(false);
      }
    };

    if (roomId) initChatRoom();
  }, [roomId]);

  // 3. 메시지 전송 로직
  const handleSend = () => {
    if (!inputText.trim()) return;
    
    sendMessage(inputText.trim());
    setInputText('');
  };

  // 메시지가 추가될 때마다 자동으로 맨 아래로 스크롤
  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => {
        flatListRef.current?.scrollToEnd({ animated: true });
      }, 100);
    }
  }, [messages]);

  // 4. 말풍선 렌더링 함수
  const renderMessage = ({ item, index }: { item: any, index: number }) => {
    if (!item) return null;

    // 공통 텍스트 확인을 통한 시스템 메시지 분기 (입장/개설 알림)
    const isSystemMessage = 
      item.messageType === 'ENTER' || 
      item.messageType === 'SYSTEM' || 
      (item.content && (item.content.includes('입장했습니다') || item.content.includes('개설했습니다')));

    // 🔘 시스템 메시지 레이아웃 (가운데 정렬)
    if (isSystemMessage) {
      return (
        <View style={styles.systemMessageContainer}>
          <View style={styles.systemMessageBadge}>
            <Text style={styles.systemMessageText}>{item.content}</Text>
          </View>
        </View>
      );
    }

    // 발신자 계정 식별을 통한 좌우 배치 판별
    const isMyMessage = item.senderAccountId === myAccountId;

    // 시간 데이터 포맷팅
    const timeString = item.sentAt 
      ? new Date(item.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      : '';

    // 연속된 말풍선 프로필 그룹화 조건
    const isSameSenderAsPrev = index > 0 && 
      messages[index - 1]?.senderAccountId === item.senderAccountId;

    // 🟢 내가 보낸 메시지 (오른쪽 초록색 말풍선)
    if (isMyMessage) {
      return (
        <View style={styles.myMessageRow}>
          <Text style={styles.timeText}>{timeString}</Text>
          <View style={styles.myBubble}>
            <Text style={styles.myMessageText}>{item.content}</Text>
          </View>
        </View>
      );
    }

    // ⚪ 상대방이 보낸 메시지 (왼쪽 흰색 말풍선)
    return (
      <View style={[styles.otherMessageRow, isSameSenderAsPrev && { marginTop: 4 }]}>
        {!isSameSenderAsPrev ? (
          <Image 
            source={{ 
              uri: item.senderProfileImageUrl && item.senderProfileImageUrl !== '/default-profile.png' 
                ? item.senderProfileImageUrl 
                : 'https://via.placeholder.com/40' 
            }} 
            style={styles.profileImage} 
          />
        ) : (
          <View style={styles.profilePlaceholder} />
        )}
        
        <View style={styles.otherMessageContent}>
          {!isSameSenderAsPrev && (
            <Text style={styles.senderName}>{item.senderName || '익명 이웃'}</Text>
          )}
          <View style={styles.otherBubbleRow}>
            <View style={styles.otherBubble}>
              <Text style={styles.otherMessageText}>{item.content}</Text>
            </View>
            <Text style={styles.timeText}>{timeString}</Text>
          </View>
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>동네 모임 채팅방</Text>
        <TouchableOpacity style={styles.menuButton}>
          <Ionicons name="menu" size={24} color="#333" />
        </TouchableOpacity>
      </View>

      {/* 키보드 회피 뷰 */}
      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'} 
        keyboardVerticalOffset={Platform.OS === 'ios' ? 100 : 0}
      >

        <View style={{ flex: 1, justifyContent: 'space-between' }}>
          
          {isLoading ? (
            <View style={styles.centerLoading}>
              <ActivityIndicator size="large" color="#1B854A" />
            </View>
          ) : (
            // 채팅 영역 전체를 하나의 flex: 1 뷰로 묶어 키보드가 밀고 올라올 때 유연하게 줄어들게 합니다.
            <View style={{ flex: 1 }}>
              {!isConnected && (
                <View style={styles.connectingBanner}>
                  <ActivityIndicator size="small" color="#666" style={{ marginRight: 8 }} />
                  <Text style={styles.connectingText}>실시간 채팅 서버에 연결 중...</Text>
                </View>
              )}
              
              <FlatList
                ref={flatListRef}
                data={messages}
                keyExtractor={(item, index) => item.messageId?.toString() || index.toString()}
                renderItem={renderMessage}
                // flexGrow: 1을 추가하여 메시지가 적을 때도 영역을 꽉 채우고, 키보드가 오면 부드럽게 리사이즈되게 합니다.
                contentContainerStyle={[styles.messageList, { flexGrow: 1 }]}
                showsVerticalScrollIndicator={false}
                onLayout={() => flatListRef.current?.scrollToEnd({ animated: false })}
              />
            </View>
          )}

          {/* 입력창 (항상 최하단 고정) */}
          <View style={[styles.inputContainer, { paddingBottom: Math.max(insets.bottom, 10) }]}>
            <TouchableOpacity style={styles.attachButton}>
              <Ionicons name="add" size={26} color="#888" />
            </TouchableOpacity>
            
            <View style={styles.textInputWrapper}>
              <TextInput
                style={styles.textInput}
                placeholder="메시지를 입력하세요"
                value={inputText}
                onChangeText={setInputText}
                multiline
                maxLength={500}
              />
              <TouchableOpacity 
                style={[styles.sendButton, !inputText.trim() && { backgroundColor: '#E0E0E0' }]} 
                onPress={handleSend}
                disabled={!inputText.trim() || !isConnected}
              >
                <Ionicons name="paper-plane" size={16} color="#FFF" />
              </TouchableOpacity>
            </View>
          </View>

        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F6F8' },
  keyboardAvoidingView: { flex: 1 },
  centerLoading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#FFF', paddingHorizontal: 15, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#EAEAEA' },
  backButton: { padding: 4 },
  headerTitle: { fontSize: 17, color: '#333' },
  menuButton: { padding: 4 },

  connectingBanner: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', backgroundColor: '#F0F0F0', paddingVertical: 8 },
  connectingText: { fontSize: 13, color: '#666' },

  messageList: { paddingHorizontal: 15, paddingVertical: 20 },

  // 시스템 알림 스타일
  systemMessageContainer: { alignItems: 'center', marginVertical: 10, width: '100%' },
  systemMessageBadge: { backgroundColor: '#E5E5EA', paddingHorizontal: 14, paddingVertical: 6, borderRadius: 16 },
  systemMessageText: { fontSize: 12, color: '#555', textAlign: 'center' },

  // 상대방 말풍선 스타일
  otherMessageRow: { flexDirection: 'row', marginBottom: 12 },
  profileImage: { width: 36, height: 36, borderRadius: 18, marginRight: 8, backgroundColor: '#DDD' },
  profilePlaceholder: { display: 'none' },
  otherMessageContent: { flex: 1, alignItems: 'flex-start' },
  senderName: { fontSize: 13, color: '#555', marginBottom: 4 },
  otherBubbleRow: { flexDirection: 'row', alignItems: 'flex-end' },
  otherBubble: { backgroundColor: '#FFF', paddingHorizontal: 14, paddingVertical: 10, borderRadius: 16, borderTopLeftRadius: 4, maxWidth: '80%', shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 2, elevation: 1 },
  otherMessageText: { fontSize: 15, color: '#333', lineHeight: 20 },
  
  // 내 말풍선 스타일
  myMessageRow: { flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'flex-end', marginBottom: 12 },
  myBubble: { backgroundColor: '#1B854A', paddingHorizontal: 14, paddingVertical: 10, borderRadius: 16, borderTopRightRadius: 4, maxWidth: '75%', shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 2, elevation: 1 },
  myMessageText: { fontSize: 15, color: '#FFF', lineHeight: 20 },
  
  timeText: { fontSize: 11, color: '#999', marginHorizontal: 6, marginBottom: 2 },

  inputContainer: { flexDirection: 'row', alignItems: 'flex-end', backgroundColor: '#FFF', paddingHorizontal: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: '#EAEAEA' },
  attachButton: { padding: 8, marginBottom: 4 },
  textInputWrapper: { flex: 1, flexDirection: 'row', alignItems: 'flex-end', backgroundColor: '#F5F6F8', borderRadius: 20, paddingLeft: 16, paddingRight: 6, paddingVertical: 6, marginLeft: 4, marginBottom: 10 },
  textInput: { flex: 1, fontSize: 15, color: '#333', maxHeight: 100, minHeight: 28, paddingTop: Platform.OS === 'ios' ? 5 : 0 },
  sendButton: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#1B854A', justifyContent: 'center', alignItems: 'center', marginLeft: 8, marginBottom: 2 },
});