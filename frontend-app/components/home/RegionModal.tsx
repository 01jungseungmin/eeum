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
          
          {(regions || []).map((item: any) => (
            <TouchableOpacity key={item.accountRegionId} style={styles.regionItem} onPress={() => onSetPrimary(item.accountRegionId)}>
              <View style={styles.regionLeft}>
                <View style={[styles.radio, item.isPrimary && styles.radioActive]} />
                <Text style={item.isPrimary ? styles.regionNameActive : styles.regionName}>
                  {/* 2. 방어막: item 안에 region 데이터가 없을 때를 대비해 ?(옵셔널 체이닝) 추가 */}
                  {item.region?.name || '동네 로딩 중...'}
                </Text>
                {item.verified && <Ionicons name="checkmark-circle" size={14} color="#00A859" style={{marginLeft: 5}} />}
              </View>
              
              <TouchableOpacity onPress={() => onDeleteRegion(item.accountRegionId)}>
                <Ionicons name="close" size={20} color="#999" />
              </TouchableOpacity>
            </TouchableOpacity>
          ))}

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