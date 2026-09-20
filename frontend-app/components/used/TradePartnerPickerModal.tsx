import React, { useEffect, useState } from 'react';
import { View, StyleSheet, Modal, TouchableOpacity, FlatList, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';
import { chatApi } from '../../api/chat';

export interface TradePartner {
  accountId: number;
  nickname: string;
}

interface Props {
  visible: boolean;
  usedProductId: number;
  /** 나 자신은 거래 상대가 될 수 없다(서버도 막는다). */
  myAccountId: number | null;
  onClose: () => void;
  /** 상대를 고르지 않고 진행하면 partner가 null이다. */
  onConfirm: (partner: TradePartner | null) => void;
}

/**
 * 판매완료 시 거래 상대를 고른다.
 *
 * 후보는 이 게시글로 문의해 온 사람들 — 내 채팅방 중 refType이 USED_PRODUCT이고
 * refId가 이 게시글인 방의 상대방이다. 서버에 게시글 기준으로 방을 거르는 API가
 * 없어 목록을 받아 앱에서 걸러낸다.
 *
 * 상대를 지정해야 그 사람이 후기를 쓸 수 있다. 다만 앱 밖에서 성사된 거래도
 * 있어 건너뛰기를 막지는 않는다.
 */
export default function TradePartnerPickerModal({
  visible,
  usedProductId,
  myAccountId,
  onClose,
  onConfirm,
}: Props) {
  const [partners, setPartners] = useState<TradePartner[]>([]);
  const [selected, setSelected] = useState<TradePartner | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!visible) return;

    let cancelled = false;
    setIsLoading(true);
    setSelected(null);

    const load = async () => {
      try {
        // 문의방은 거래가 끝나면 닫힐 수 있다. 닫힌 방의 상대도 후보여야 한다.
        const rooms: any[] = [];
        let cursorValue: string | null = null;
        let cursorId: number | null = null;

        // 방이 많은 계정도 있어 몇 페이지만 훑는다.
        for (let page = 0; page < 5; page++) {
          const slice = await chatApi.getRooms(cursorValue, cursorId, 50, true);
          rooms.push(...slice.content);
          if (!slice.hasNext) break;
          cursorValue = slice.nextCursorValue;
          cursorId = slice.nextCursorId;
        }

        const targetRooms = rooms.filter(
          (room) => room.refType === 'USED_PRODUCT' && Number(room.refId) === usedProductId
        );

        const found: TradePartner[] = [];
        for (const room of targetRooms) {
          const detail = await chatApi.getRoomDetail(room.roomId);
          const other = (detail?.participants ?? []).find(
            (p: any) => p.accountId !== myAccountId
          );
          if (other && !found.some((f) => f.accountId === other.accountId)) {
            found.push({
              accountId: other.accountId,
              nickname: other.nickname || other.name || '알 수 없음',
            });
          }
        }

        if (!cancelled) setPartners(found);
      } catch (error) {
        console.error('거래 상대 후보 조회 실패:', error);
        if (!cancelled) setPartners([]);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    load();
    return () => { cancelled = true; };
  }, [visible, usedProductId, myAccountId]);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <TouchableOpacity style={styles.backdropTouch} activeOpacity={1} onPress={onClose} />

        <View style={styles.sheet}>
          <View style={styles.header}>
            <Text fontWeight="bold" style={styles.title}>거래 상대 선택</Text>
            <TouchableOpacity onPress={onClose} hitSlop={8}>
              <Ionicons name="close" size={24} color="#333" />
            </TouchableOpacity>
          </View>

          <Text style={styles.guide}>
            상대를 지정해야 그 분이 거래 후기를 남길 수 있어요.
          </Text>

          {isLoading ? (
            <ActivityIndicator style={{ marginVertical: 40 }} color="#00A859" />
          ) : (
            <FlatList
              data={partners}
              keyExtractor={(item) => String(item.accountId)}
              style={styles.list}
              renderItem={({ item }) => {
                const isSelected = selected?.accountId === item.accountId;
                return (
                  <TouchableOpacity
                    style={[styles.row, isSelected && styles.rowSelected]}
                    onPress={() => setSelected(item)}
                  >
                    <View style={styles.avatar}>
                      <Ionicons name="person" size={18} color="#FFF" />
                    </View>
                    <Text style={styles.nickname}>{item.nickname}</Text>
                    {isSelected && <Ionicons name="checkmark-circle" size={22} color="#00A859" />}
                  </TouchableOpacity>
                );
              }}
              ListEmptyComponent={
                <View style={styles.empty}>
                  <Text style={styles.emptyText}>이 글로 문의한 사람이 없어요.</Text>
                  <Text style={styles.emptySub}>상대 없이 완료하면 후기를 받을 수 없어요.</Text>
                </View>
              }
            />
          )}

          <View style={styles.actions}>
            <TouchableOpacity style={[styles.button, styles.buttonOutline]} onPress={() => onConfirm(null)}>
              <Text style={styles.buttonOutlineText}>상대 없이 완료</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, selected ? styles.buttonPrimary : styles.buttonDisabled]}
              disabled={!selected}
              onPress={() => onConfirm(selected)}
            >
              <Text style={styles.buttonPrimaryText}>이 사람과 완료</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  backdropTouch: { flex: 1 },
  sheet: { backgroundColor: '#FFF', borderTopLeftRadius: 16, borderTopRightRadius: 16, paddingBottom: 30, maxHeight: '75%' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 20, paddingBottom: 12 },
  title: { fontSize: 17, color: '#1E3932' },
  guide: { fontSize: 13, color: '#888', paddingHorizontal: 20, paddingBottom: 12 },
  list: { paddingHorizontal: 20 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 14, paddingHorizontal: 12, borderRadius: 10 },
  rowSelected: { backgroundColor: '#F4FBF7' },
  avatar: { width: 36, height: 36, borderRadius: 18, backgroundColor: '#C8C8C8', alignItems: 'center', justifyContent: 'center' },
  nickname: { flex: 1, fontSize: 15, color: '#333' },
  empty: { alignItems: 'center', paddingVertical: 40, gap: 6 },
  emptyText: { fontSize: 15, color: '#999' },
  emptySub: { fontSize: 13, color: '#BBB' },
  actions: { flexDirection: 'row', gap: 10, paddingHorizontal: 20, paddingTop: 12 },
  button: { flex: 1, height: 50, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  buttonOutline: { borderWidth: 1, borderColor: '#DDD', backgroundColor: '#FFF' },
  buttonOutlineText: { fontSize: 15, color: '#666' },
  buttonPrimary: { backgroundColor: '#00A859' },
  buttonDisabled: { backgroundColor: '#CFCFCF' },
  buttonPrimaryText: { fontSize: 15, color: '#FFF', fontWeight: 'bold' },
});
