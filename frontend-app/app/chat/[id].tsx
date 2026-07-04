import React, { useState, useEffect, useRef } from 'react';
import { 
  View, StyleSheet, TextInput, TouchableOpacity, FlatList, 
  KeyboardAvoidingView, Platform, ActivityIndicator, Image, Alert,
  Text as RNText, Modal, Keyboard, AppState
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
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

  const [isKeyboardVisible, setKeyboardVisible] = useState(false);

  // 1. STOMP 훅 불러오기
  const { messages, setMessages, isConnected, sendMessage } = useChatStomp(roomId);

  const [inputText, setInputText] = useState('');
  const [myAccountId, setMyAccountId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  const [isMenuVisible, setIsMenuVisible] = useState(false);
  const [participants, setParticipants] = useState<any[]>([]);
  
  const flatListRef = useRef<FlatList>(null);
  
  // 앱 상태 감지용 ref
  const appState = useRef(AppState.currentState);

  // [NEW] 이전 대화 불러오기 중복 방지용 상태
  const [isFetchingOlder, setIsFetchingOlder] = useState(false);

  useEffect(() => {
    const keyboardDidShowListener = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow',
      () => setKeyboardVisible(true)
    );
    const keyboardDidHideListener = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide',
      () => setKeyboardVisible(false)
    );

    return () => {
      keyboardDidHideListener.remove();
      keyboardDidShowListener.remove();
    };
  }, []);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', async (nextAppState) => {
      if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
        if (roomId) {
          try {
            const pastMessages = await chatApi.getPastMessages(roomId);
            setMessages(pastMessages.reverse()); 
            chatApi.markAsRead(roomId).catch(() => {});
          } catch (error) {
            console.error('최신 메시지 동기화 실패:', error);
          }
        }
      }
      appState.current = nextAppState;
    });

    return () => subscription.remove();
  }, [roomId]);

  useEffect(() => {
    const initChatRoom = async () => {
      try {
        setIsLoading(true);
        const myInfo = await userApi.getMyInfo();
        setMyAccountId(myInfo.accountId);

        const roomDetail = await chatApi.getRoomDetail(roomId);
        
        if (roomDetail && roomDetail.participants) {
          const realParticipants = roomDetail.participants.map((p: any) => ({
            id: p.accountId,
            name: p.nickname || p.name || '알 수 없음',
            isHost: p.accountId === roomDetail.createdBy, 
            profileUrl: p.profileImageUrl
          }));
          setParticipants(realParticipants);
        }

        const pastMessages = await chatApi.getPastMessages(roomId);
        setMessages(pastMessages.reverse()); 
        
        if (roomId) {
          chatApi.markAsRead(roomId).catch(err => console.error('읽음 처리 에러:', err));
        }
      } catch (error: any) {
        console.error('채팅방 초기화 에러:', error);
        if (error.response?.status === 403) {
          Alert.alert('입장 불가', '채팅방 참여자가 아닙니다.', [{ text: '확인', onPress: () => router.back() }]);
        } else {
          Alert.alert('오류', '채팅방 정보를 불러올 수 없습니다.', [{ text: '확인', onPress: () => router.back() }]);
        }
      } finally {
        setIsLoading(false);
      }
    };

    if (roomId) initChatRoom();
  }, [roomId]);

  useEffect(() => {
    if (roomId && messages.length > 0) {
      chatApi.markAsRead(roomId).catch(() => {});
    }
  }, [messages, roomId]);

  // [NEW] 이전 50개 메시지 불러오기 로직 (커서 페이징)
  const loadMoreMessages = async () => {
    if (messages.length === 0 || isFetchingOlder) return;

    // 현재 렌더링된 메시지 중 가장 오래된 메시지 (배열 맨 앞 데이터)
    const oldestMessage = messages[0];
    const cursorTime = oldestMessage.sentAt || oldestMessage.createdAt;

    if (!cursorTime) return;

    try {
      setIsFetchingOlder(true);
      
      const olderMessages = await chatApi.getPastMessages(roomId, cursorTime);
      
      if (olderMessages && olderMessages.length > 0) {
        // 새로 가져온 과거 메시지를 최신순->과거순에 맞게 뒤집은 뒤, 기존 배열 앞에 붙여줍니다.
        setMessages((prev) => [...olderMessages.reverse(), ...prev]);
      }
    } catch (error) {
      console.error('이전 메시지 로딩 실패:', error);
    } finally {
      setIsFetchingOlder(false);
    }
  };

  const handleSend = () => {
    if (!inputText.trim()) return;
    sendMessage(inputText.trim());
    setInputText('');
  };

  const handleAttachImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('권한 필요', '사진을 보내려면 갤러리 접근 권한이 필요합니다.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: false, 
      quality: 0.8, 
    });

    if (!result.canceled) {
      const selectedImageUri = result.assets[0].uri;
      try {
        const targetImageUrl = selectedImageUri; 
        await chatApi.sendImageMessage(roomId, targetImageUrl);
      } catch (error) {
        console.error('이미지 전송 실패:', error);
        Alert.alert('오류', '사진 전송 중 문제가 발생했습니다.');
      }
    }
  };

  const handleLeaveRoom = async () => {
    Alert.alert(
      '채팅방 나가기',
      '나가기를 하면 대화내용이 모두 삭제되고 채팅목록에서도 삭제됩니다. 정말 나가시겠습니까?',
      [
        { text: '취소', style: 'cancel' },
        { 
          text: '나가기', 
          style: 'destructive',
          onPress: async () => {
            try {
              await chatApi.leaveRoom(roomId); 
              setIsMenuVisible(false);
              router.replace('/chat'); 
            } catch (error) {
              console.error('채팅방 나가기 실패:', error);
              Alert.alert('오류', '채팅방을 나가지 못했습니다.');
            }
          }
        }
      ]
    );
  };

  useEffect(() => {
    // 메시지가 새로 추가되었을 때 스크롤 맨 아래로 이동
    // (이전 대화 로딩 중일 때는 맨 아래로 내려가지 않게 조건 추가)
    if (messages.length > 0 && !isFetchingOlder) {
      setTimeout(() => {
        flatListRef.current?.scrollToEnd({ animated: true });
      }, 100);
    }
  }, [messages]);

  const renderMessage = ({ item, index }: { item: any, index: number }) => {
    if (!item) return null;

    const isSystemMessage = 
      item.messageType === 'ENTER' || 
      item.messageType === 'SYSTEM' || 
      (item.content && (item.content.includes('입장했습니다') || item.content.includes('개설했습니다')));

    if (isSystemMessage) {
      return (
        <View style={styles.systemMessageContainer}>
          <View style={styles.systemMessageBadge}>
            <Text style={styles.systemMessageText}>{item.content}</Text>
          </View>
        </View>
      );
    }

    const currentSenderId = item.senderAccountId || item.senderId || item.memberId || item.userId;
    const displayName = item.senderName || item.nickname || item.name || '알 수 없음';
    const profileImgUrl = item.senderProfileImageUrl || item.profileImageUrl || item.imageUrl;
    const isMyMessage = currentSenderId === myAccountId;
    const timeString = item.sentAt || item.createdAt ? new Date(item.sentAt || item.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '';

    const prevItem = index > 0 ? messages[index - 1] : null;
    const isPrevSystem = prevItem ? (prevItem.messageType === 'ENTER' || prevItem.messageType === 'SYSTEM' || (prevItem.content && prevItem.content.includes('입장했습니다'))) : false;
    const prevSenderId = prevItem ? (prevItem.senderAccountId || prevItem.senderId || prevItem.memberId || prevItem.userId) : null;
    
    const isSameSenderAsPrev = index > 0 && !isPrevSystem && currentSenderId !== undefined && currentSenderId !== null && prevSenderId === currentSenderId;

    if (isMyMessage) {
      return (
        <View style={styles.myMessageRow}>
          <View style={styles.timeContainerMy}>
            <Text style={styles.timeText}>{timeString}</Text>
          </View>
          <View style={[styles.myBubble, item.messageType === 'IMAGE' && { backgroundColor: 'transparent', paddingHorizontal: 0, paddingVertical: 0 }]}>
            {item.messageType === 'IMAGE' ? (
              <Image source={{ uri: item.content }} style={styles.messageImage} />
            ) : (
              <Text style={styles.myMessageText}>{item.content}</Text>
            )}
          </View>
        </View>
      );
    }

    return (
      <View style={[styles.otherMessageRow, { marginTop: isSameSenderAsPrev ? 4 : 16 }]}>
        {!isSameSenderAsPrev ? (
          profileImgUrl && profileImgUrl !== '/default-profile.png' ? (
             <Image source={{ uri: profileImgUrl }} style={styles.profileImage} />
          ) : (
            <View style={styles.defaultProfile}>
              <Ionicons name="person" size={20} color="#FFF" />
            </View>
          )
        ) : (
          <View style={styles.profilePlaceholder} />
        )}

        <View style={styles.otherMessageContent}>
          {!isSameSenderAsPrev && <Text style={styles.senderName}>{displayName}</Text>}
          <View style={styles.otherBubbleRow}>
            <View style={[styles.otherBubble, item.messageType === 'IMAGE' && { backgroundColor: 'transparent', paddingHorizontal: 0, paddingVertical: 0 }]}>
              {item.messageType === 'IMAGE' ? (
                <Image source={{ uri: item.content }} style={styles.messageImage} />
              ) : (
                <Text style={styles.otherMessageText}>{item.content}</Text>
              )}
            </View>
            <View style={styles.timeContainerOther}>
               <Text style={styles.timeText}>{timeString}</Text>
            </View>
          </View>
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>동네 모임 채팅방</Text>
        
        <TouchableOpacity style={styles.menuButton} onPress={() => setIsMenuVisible(true)}>
          <Ionicons name="menu" size={24} color="#333" />
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior="padding" 
        keyboardVerticalOffset={Platform.OS === 'ios' ? 100 : -50}
        enabled={Platform.OS === 'ios' ? true : isKeyboardVisible} 
      >
        <View style={{ flex: 1, justifyContent: 'space-between' }}>
          {isLoading ? (
            <View style={styles.centerLoading}>
              <ActivityIndicator size="large" color="#1B854A" />
            </View>
          ) : (
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
                contentContainerStyle={[styles.messageList, { flexGrow: 1 }]}
                showsVerticalScrollIndicator={false}
                onLayout={() => flatListRef.current?.scrollToEnd({ animated: false })}
                // [NEW] 화면 맨 위로 당겨서 새로고침(이전 대화 로드) 기능 활성화
                refreshing={isFetchingOlder}
                onRefresh={loadMoreMessages}
              />
            </View>
          )}

          <View style={[styles.inputContainer, { paddingBottom: Platform.OS === 'ios' ? Math.max(insets.bottom, 10) : 12 }]}>
            <TouchableOpacity style={styles.attachButton} onPress={handleAttachImage}>
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
              <TouchableOpacity style={[styles.sendButton, !inputText.trim() && { backgroundColor: '#E0E0E0' }]} onPress={handleSend} disabled={!inputText.trim() || !isConnected}>
                <Ionicons name="paper-plane" size={16} color="#FFF" />
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>

      <Modal visible={isMenuVisible} animationType="slide" transparent={true} onRequestClose={() => setIsMenuVisible(false)}>
        <View style={styles.modalOverlay}>
          <TouchableOpacity style={styles.modalBackground} activeOpacity={1} onPress={() => setIsMenuVisible(false)} />
          
          <View style={styles.menuContainer}>
            <View style={styles.menuHeader}>
              <Text fontWeight="bold" style={styles.menuTitle}>대화상대 ({participants.length})</Text>
              <TouchableOpacity onPress={() => setIsMenuVisible(false)}>
                <Ionicons name="close" size={24} color="#333" />
              </TouchableOpacity>
            </View>

            <FlatList
              data={participants}
              keyExtractor={(item) => item.id.toString()}
              contentContainerStyle={{ padding: 20 }}
              renderItem={({ item }) => (
                <View style={styles.participantRow}>
                  {item.profileUrl ? (
                    <Image source={{ uri: item.profileUrl }} style={styles.participantImage} />
                  ) : (
                    <View style={styles.participantDefaultProfile}>
                      <Ionicons name="person" size={20} color="#FFF" />
                    </View>
                  )}
                  <Text style={styles.participantName}>{item.name}</Text>
                  {item.isHost && (
                    <View style={styles.hostBadge}>
                      <Text style={styles.hostBadgeText}>방장</Text>
                    </View>
                  )}
                </View>
              )}
            />

            <View style={styles.menuFooter}>
              <TouchableOpacity style={styles.footerButton}>
                <Ionicons name="images-outline" size={22} color="#555" />
                <Text style={styles.footerButtonText}>사진 모아보기</Text>
              </TouchableOpacity>
              
              <TouchableOpacity style={styles.leaveButton} onPress={handleLeaveRoom}>
                <Ionicons name="log-out-outline" size={22} color="#D9534F" />
                <Text style={styles.leaveButtonText}>나가기</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
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

  systemMessageContainer: { alignItems: 'center', marginVertical: 10, width: '100%' },
  systemMessageBadge: { backgroundColor: '#E5E5EA', paddingHorizontal: 14, paddingVertical: 6, borderRadius: 16 },
  systemMessageText: { fontSize: 12, color: '#555', textAlign: 'center' },

  otherMessageRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: 8, paddingRight: 40 },
  defaultProfile: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#BDBDBD', justifyContent: 'center', alignItems: 'center', marginRight: 10 },
  profileImage: { width: 40, height: 40, borderRadius: 20, marginRight: 10, backgroundColor: '#EEE' },
  profilePlaceholder: { width: 40, marginRight: 10 },
  otherMessageContent: { flex: 1 },
  senderName: { fontSize: 13, color: '#666', marginBottom: 4, marginLeft: 2 },
  otherBubbleRow: { flexDirection: 'row', alignItems: 'flex-end' },
  otherBubble: { backgroundColor: '#FFF', paddingHorizontal: 14, paddingVertical: 10, borderRadius: 16, borderTopLeftRadius: 4, maxWidth: '85%', shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 2, elevation: 1 },
  otherMessageText: { fontSize: 15, color: '#333', lineHeight: 20 },
  timeContainerOther: { alignItems: 'flex-start', marginLeft: 6, marginBottom: 2 },
  
  myMessageRow: { flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'flex-end', marginBottom: 8, marginTop: 4 },
  myBubble: { backgroundColor: '#1B854A', paddingHorizontal: 14, paddingVertical: 10, borderRadius: 16, borderTopRightRadius: 4, maxWidth: '75%', shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 2, elevation: 1 },
  myMessageText: { fontSize: 15, color: '#FFF', lineHeight: 20 },
  timeContainerMy: { alignItems: 'flex-end', marginRight: 6, marginBottom: 2 },
  timeText: { fontSize: 11, color: '#999' },
  messageImage: { width: 200, height: 200, borderRadius: 12, resizeMode: 'cover' },

  inputContainer: { flexDirection: 'row', alignItems: 'flex-end', backgroundColor: '#FFF', paddingHorizontal: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: '#EAEAEA' },
  attachButton: { padding: 8, marginBottom: 12 },
  textInputWrapper: { flex: 1, flexDirection: 'row', alignItems: 'flex-end', backgroundColor: '#F5F6F8', borderRadius: 20, paddingLeft: 16, paddingRight: 6, paddingVertical: 6, marginLeft: 4, marginBottom: 10 },
  textInput: { flex: 1, fontSize: 15, color: '#333', maxHeight: 100, minHeight: 28, paddingTop: Platform.OS === 'ios' ? 5 : 0 },
  sendButton: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#1B854A', justifyContent: 'center', alignItems: 'center', marginLeft: 8, marginBottom: 2 },

  modalOverlay: { flex: 1, flexDirection: 'row' },
  modalBackground: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)' },
  menuContainer: { width: '75%', backgroundColor: '#FFF', height: '100%', shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 10, elevation: 5 },
  menuHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 20, borderBottomWidth: 1, borderBottomColor: '#F0F0F0', paddingTop: Platform.OS === 'ios' ? 50 : 30 },
  menuTitle: { fontSize: 16, color: '#333' },
  participantRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  participantImage: { width: 40, height: 40, borderRadius: 20, marginRight: 12, backgroundColor: '#EEE' },
  participantDefaultProfile: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#BDBDBD', justifyContent: 'center', alignItems: 'center', marginRight: 12 },
  participantName: { fontSize: 15, color: '#333' },
  hostBadge: { backgroundColor: '#E8F5E9', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginLeft: 8 },
  hostBadgeText: { fontSize: 11, color: '#1B854A', fontWeight: 'bold' },
  menuFooter: { borderTopWidth: 1, borderTopColor: '#F0F0F0', padding: 20, paddingBottom: 40 },
  footerButton: { flexDirection: 'row', alignItems: 'center', marginBottom: 20 },
  footerButtonText: { fontSize: 15, color: '#555', marginLeft: 10 },
  leaveButton: { flexDirection: 'row', alignItems: 'center' },
  leaveButtonText: { fontSize: 15, color: '#D9534F', marginLeft: 10 },
});