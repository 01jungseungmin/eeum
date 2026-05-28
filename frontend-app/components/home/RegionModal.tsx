import React from 'react';
import { View, StyleSheet, Modal, Pressable, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

interface RegionModalProps {
  visible: boolean;
  onClose: () => void;
  regions: any[];
  onSetPrimary: (id: number) => void;
  onAddRegion: () => void;
  onDeleteRegion: (id: number) => void;
}

export default function RegionModal({
  visible,
  onClose,
  regions,
  onSetPrimary,
  onAddRegion,
  onDeleteRegion
}: RegionModalProps) {
  return (
    <Modal animationType="slide" transparent={true} visible={visible} onRequestClose={onClose}>
      <Pressable style={styles.modalOverlay} onPress={onClose}>
        <Pressable style={styles.modalContent}>
          <Text style={styles.modalTitle}>내 동네 설정</Text>
          <Text style={styles.modalSubTitle}>최대 2개의 동네를 선택할 수 있어요</Text>
          
          {/* 💡 방어막 1: regions 배열이 없으면 빈 배열로 치환 */}
          {(regions || []).map((item: any) => {
            // 💡 방어막 2: item 자체가 아예 없으면 렌더링 건너뛰기
            if (!item) return null; 
            
            return (
              <TouchableOpacity 
                key={item?.accountRegionId || Math.random().toString()} 
                style={styles.regionItem} 
                onPress={() => item?.accountRegionId && onSetPrimary(item.accountRegionId)}
              >
                <View style={styles.regionLeft}>
                  <View style={[styles.radio, item?.isPrimary && styles.radioActive]} />
                  <Text style={item?.isPrimary ? styles.regionNameActive : styles.regionName}>
                    {/* 💡 방어막 3: ? 기호를 총동원해서 에러 원천 차단 */}
                    {item?.dong || item?.fullName || item?.region?.dong || item?.region?.name || '동네 정보 없음'}
                  </Text>
                  {item?.verified && <Ionicons name="checkmark-circle" size={14} color="#00A859" style={{marginLeft: 5}} />}
                </View>
                
                <TouchableOpacity onPress={() => item?.accountRegionId && onDeleteRegion(item.accountRegionId)}>
                  <Ionicons name="close" size={20} color="#999" />
                </TouchableOpacity>
              </TouchableOpacity>
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
  regionLeft: { flexDirection: 'row', alignItems: 'center' },
  radio: { width: 18, height: 18, borderRadius: 9, backgroundColor: '#E0E0E0', marginRight: 10 },
  radioActive: { backgroundColor: '#00A859' },
  regionName: { fontSize: 15, color: '#666' },
  regionNameActive: { fontSize: 15, color: '#333', fontWeight: 'bold' },
  addButton: { backgroundColor: '#00A859', flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 15, borderRadius: 8, marginTop: 20 },
  addButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold', marginLeft: 5 }
});