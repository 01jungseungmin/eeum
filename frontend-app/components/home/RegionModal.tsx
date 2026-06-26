import React from 'react';
import { View, StyleSheet, Modal, Pressable, TouchableOpacity, Alert } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

interface RegionModalProps {
  visible: boolean;
  onClose: () => void;
  regions: any[];
  viewingRegionId?: number | null;
  onSelectView: (region: any) => void;
  onSetPrimary: (id: number) => void;
  onAddRegion: () => void;
  onDeleteRegion: (id: number) => void;
  onVerifyRegion: (id: number) => void;
}

export default function RegionModal({
  visible,
  onClose,
  regions,
  viewingRegionId,
  onSelectView,
  onSetPrimary,
  onAddRegion,
  onDeleteRegion,
  onVerifyRegion
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
            const isVerified = item?.verified; 
            const isViewing = viewingRegionId === targetId; // ✨ 현재 보고 있는 지역인지 확인
            
            return (
              <View key={targetId || Math.random().toString()} style={styles.regionItem}>
                
                {/* 왼쪽 영역: 누르면 화면(View)만 부드럽게 변경 */}
                <TouchableOpacity 
                  style={styles.regionLeft} 
                  onPress={() => targetId && onSelectView(item)}
                >
                  <View style={[
                    styles.radio, 
                    isViewing && styles.radioActive, // 보고 있는 동네에 불 켜기
                  ]} />
                  <Text style={isViewing ? styles.regionNameActive : styles.regionName}>
                    {item?.dong || item?.fullName || '동네 정보 없음'}
                  </Text>
                  
                  {/* 대표 동네일 경우 뱃지 표시 */}
                  {item?.isPrimary && (
                    <Text style={styles.primaryBadge}>대표</Text>
                  )}
                </TouchableOpacity>
                
                {/* 오른쪽 영역: 인증 안 됐으면 [인증하기], 됐는데 대표 아니면 [대표 설정] */}
                <View style={styles.regionRight}>
                  {!isVerified ? (
                    <TouchableOpacity style={styles.verifyBtn} onPress={() => targetId && onVerifyRegion(targetId)}>
                      <Text style={styles.verifyBtnText}>인증하기</Text>
                    </TouchableOpacity>
                  ) : !item?.isPrimary ? (
                    <TouchableOpacity style={[styles.verifyBtn, { backgroundColor: '#E0F2F1' }]} onPress={() => targetId && onSetPrimary(targetId)}>
                      <Text style={[styles.verifyBtnText, { color: '#00897B' }]}>대표 설정</Text>
                    </TouchableOpacity>
                  ) : null}
                  
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
  regionName: { fontSize: 15, color: '#666' },
  regionNameActive: { fontSize: 15, color: '#333', fontWeight: 'bold' },
  primaryBadge: { fontSize: 10, color: '#00A859', borderWidth: 1, borderColor: '#00A859', paddingHorizontal: 4, borderRadius: 4, marginLeft: 6 },
  addButton: { backgroundColor: '#00A859', flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 15, borderRadius: 8, marginTop: 20 },
  addButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold', marginLeft: 5 },
  verifyBtn: { backgroundColor: '#E8F5E9', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 4 },
  verifyBtnText: { color: '#00A859', fontSize: 12, fontWeight: 'bold' }
});