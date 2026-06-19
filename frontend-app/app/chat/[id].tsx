import React, { useState } from 'react';
import { View, TextInput, TouchableOpacity, FlatList, StyleSheet } from 'react-native';
import { Text } from '../../components/CustomText';
import { useChat } from '../../hooks/useChat';
import { useLocalSearchParams } from 'expo-router';
// import { useAuth } from '../../hooks/useAuth'; // 토큰을 가져오는 훅이 있다고 가정

export default function ChatRoomScreen() {
  const { id } = useLocalSearchParams();
  // 라우터나 파라미터에서 roomId를 받아옴
  const roomId = id as string; // 테스트용 더미 ID
  const token = 'eyJhbG...'; // 실제로는 AsyncStorage나 전역 상태에서 가져온 Access Token
  
  const [inputText, setInputText] = useState('');
  
  // 방금 만든 STOMP 훅 사용!
  const { messages, isConnected, sendMessage } = useChat(roomId, token);

  const handleSend = () => {
    if (!inputText.trim()) return;
    sendMessage(inputText); // 서버로 발행(/pub)
    setInputText('');
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text>상태: {isConnected ? '🟢 연결됨' : '🔴 연결 중...'}</Text>
      </View>

      <FlatList
        data={messages}
        keyExtractor={(item, index) => index.toString()}
        renderItem={({ item }) => (
          <View style={styles.messageBubble}>
            <Text>{item.content}</Text> {/* 백엔드에서 보내주는 키값에 맞춰 수정 */}
          </View>
        )}
      />

      <View style={styles.inputArea}>
        <TextInput
          style={styles.input}
          value={inputText}
          onChangeText={setInputText}
          placeholder="메시지를 입력하세요"
        />
        <TouchableOpacity onPress={handleSend} style={styles.sendBtn}>
          <Text style={{color: 'white'}}>전송</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { padding: 15, borderBottomWidth: 1, borderColor: '#eee' },
  messageBubble: { padding: 10, margin: 10, backgroundColor: '#F0F0F0', borderRadius: 8, alignSelf: 'flex-start' },
  inputArea: { flexDirection: 'row', padding: 10, borderTopWidth: 1, borderColor: '#eee' },
  input: { flex: 1, borderWidth: 1, borderColor: '#ccc', borderRadius: 20, paddingHorizontal: 15 },
  sendBtn: { backgroundColor: '#1B854A', justifyContent: 'center', paddingHorizontal: 15, borderRadius: 20, marginLeft: 10 }
});