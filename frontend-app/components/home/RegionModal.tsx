import React from 'react';
import { View, StyleSheet, Modal, Pressable, TouchableOpacity, Alert } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

interface RegionModalProps {
  visible: boolean;
  onClose: () => void;
  regions: any[];
  onSetPrimary: (id: number) => void;
  onAddRegion: () => void;
  onDeleteRegion: (id: number) => void;
  onVerifyRegion: (id: number) => void; // 🔥 1. 인터페이스에 추가
}

export default function RegionModal({
  visible,
  onClose,
  regions,
  onSetPrimary,
  onAddRegion,
  onDeleteRegion,
  onVerifyRegion // 🔥 2. Props 받아오기
}: RegionModalProps) {
  return (
    <Modal animationType="slide" transparent={true} visible={visible} onRequestClose={onClose}>
      <Pressable style={styles.modalOverlay} onPress={onClose}>
        <Pressable style={styles.modalContent}>
          <Text style={styles.modalTitle}>내 동네 설정</Text>
          <Text style={styles.modalSubTitle}>최대 2개의 동네를 선택할 수 있어요</Text>
          
          {(regions || []).map((item: any) => {
            if (!item) return null; 
            
            const targetId = item?.accountRegionId;
            const isVerified = item?.verified; // 인증 여부 변수
            
            return (
              <View key={targetId || Math.random().toString()} style={styles.regionItem}>
                {/* 동네 선택 영역: 인증된 동네만 클릭 시 대표지역 설정 기능 작동 */}
                <TouchableOpacity 
                  style={styles.regionLeft} 
                  onPress={() => {
                    if (isVerified) {
                      targetId && onSetPrimary(targetId);
                    } else {
                      Alert.alert("인증 필요", "먼저 현재 위치 GPS 인증을 완료해 주세요!");
                    }
                  }}
                >
                  <View style={[
                    styles.radio, 
                    item?.isPrimary && styles.radioActive,
                    !isVerified && styles.radioDisabled // 인증 안 된 곳은 라디오 버튼 흐리게
                  ]} />
                  <Text style={item?.isPrimary ? styles.regionNameActive : styles.regionName}>
                    {item?.dong || item?.fullName || '동네 정보 없음'}
                  </Text>
                  {isVerified && <Ionicons name="checkmark-circle" size={14} color="#00A859" style={{marginLeft: 5}} />}
                </TouchableOpacity>
                
                {/* 오른쪽 컨트롤 영역: 인증 안 됐으면 [인증하기] 버튼 표시, 옆에는 삭제(X) 버튼 */}
                <View style={styles.regionRight}>
                  {!isVerified && (
                    <TouchableOpacity 
                      style={styles.verifyBtn} 
                      onPress={() => targetId && onVerifyRegion(targetId)}
                    >
                      <Text style={styles.verifyBtnText}>인증하기</Text>
                    </TouchableOpacity>
                  )}
                  
                  <TouchableOpacity onPress={() => targetId && onDeleteRegion(targetId)} style={{ marginLeft: 12 }}>
                    <Ionicons name="close" size={20} color="#999" />
                  </TouchableOpacity>
                </View>
              </View>
            );
          })}

          <TouchableOpacity style={styles.addButton} onPress={onAddRegion}>
            <Ionicons name="add" size={20} color="#fff" />
            <Text style={styles.addButtonText}>동네 추가</Text>
          </TouchableOpacity>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: '#fff', borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 25, paddingBottom: 40 },
  modalTitle: { fontSize: 18, fontWeight: 'bold', color: '#333', marginBottom: 5 },
  modalSubTitle: { fontSize: 13, color: '#888', marginBottom: 20 },
  regionItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  regionLeft: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  regionRight: { flexDirection: 'row', alignItems: 'center' },
  radio: { width: 18, height: 18, borderRadius: 9, backgroundColor: '#E0E0E0', marginRight: 10 },
  radioActive: { backgroundColor: '#00A859' },
  radioDisabled: { backgroundColor: '#F0F0F0', borderStyle: 'dashed', borderWidth: 1, borderColor: '#CCC' },
  regionName: { fontSize: 15, color: '#666' },
  regionNameActive: { fontSize: 15, color: '#333', fontWeight: 'bold' },
  addButton: { backgroundColor: '#00A859', flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 15, borderRadius: 8, marginTop: 20 },
  addButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold', marginLeft: 5 },
  // ✨ 인증 버튼 스타일 추가
  verifyBtn: { backgroundColor: '#E8F5E9', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 4 },
  verifyBtnText: { color: '#00A859', fontSize: 12, fontWeight: 'bold' }
});