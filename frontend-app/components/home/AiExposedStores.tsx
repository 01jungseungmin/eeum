import React, { useEffect, useState } from 'react';
import { View, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

import { AiExposedStore, aiExposureApi } from '../../api/aiExposure';
import { StoreThumbnail } from '../StoreThumbnail';

interface Props {
  router: any;
  /**
   * 지역 키워드. 서버가 가게 '주소'에 이 문자열이 들어가는지로 거르므로
   * 동 이름(연남동)이 아니라 구 이름(마포구)을 넘겨야 걸린다.
   */
  regionKeyword?: string | null;
}

/**
 * 생활권 매칭으로 노출 중인 가게.
 *
 * 목록 조회 자체가 서버에 노출 로그로 기록되고, 카드를 누르면 클릭 로그를 남긴다.
 * 이 둘이 이어져야 전환 추적이 되므로 requestId를 그대로 들고 다닌다.
 *
 * 노출 중인 가게가 없으면(빈 배열) 섹션째 감춘다 — 광고 영역이라 비어 있는 게
 * 정상이고, 빈 제목만 남으면 고장난 것처럼 보인다.
 */
export default function AiExposedStores({ router, regionKeyword }: Props) {
  const [stores, setStores] = useState<AiExposedStore[]>([]);

  useEffect(() => {
    let alive = true;

    aiExposureApi.getExposedStores(regionKeyword || undefined).then((list) => {
      if (alive) setStores(list);
    });

    return () => {
      alive = false;
    };
  }, [regionKeyword]);

  if (stores.length === 0) {
    return null;
  }

  const handlePress = (store: AiExposedStore) => {
    // 클릭 로그는 기다리지 않는다. 기록이 늦거나 실패해도 가게로는 들어가야 한다.
    aiExposureApi.recordClick(store.exposureStatusId, store.requestId);
    router.push(`/shop/${store.storeId}`);
  };

  return (
    <View style={styles.section}>
      <View style={styles.header}>
        <Ionicons name="sparkles" size={16} color="#00A859" style={{ marginRight: 6 }} />
        <Text fontWeight="bold" style={styles.title}>이 동네에서 눈여겨볼 곳</Text>
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.list}
      >
        {stores.map((store) => (
          <TouchableOpacity
            key={store.exposureStatusId}
            style={styles.card}
            onPress={() => handlePress(store)}
          >
            <StoreThumbnail
              uri={store.storeThumbnailUrl}
              style={styles.thumbnail}
              iconSize={28}
            />
            <View style={styles.cardBody}>
              <View style={styles.cardTop}>
                <Text fontWeight="bold" style={styles.storeName} numberOfLines={1}>
                  {store.storeName}
                </Text>
                {/* 광고성 노출이라는 점을 감추지 않는다 */}
                <Text style={styles.badge}>AI 추천</Text>
              </View>
              {!!store.interest && (
                <Text style={styles.interest} numberOfLines={1}>{store.interest}</Text>
              )}
              <Text style={styles.address} numberOfLines={1}>{store.address}</Text>
            </View>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  // 배너(marginBottom 25)와 '우리 동네 상점' 사이에 고르게 놓이도록 위아래를 맞춘다.
  // 아래 여백이 없으면 다음 구역 제목에 붙어 그 구역의 일부처럼 보인다.
  section: { paddingTop: 10, paddingBottom: 35 },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, marginBottom: 12 },
  title: { fontSize: 16, color: '#1E3932' },
  list: { paddingHorizontal: 20 },
  card: {
    width: 240,
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    marginRight: 12,
    borderRadius: 12,
    backgroundColor: '#F4FBF7',
    borderWidth: 1,
    borderColor: '#DFF1E7',
  },
  thumbnail: { width: 56, height: 56, borderRadius: 8, marginRight: 12 },
  cardBody: { flex: 1 },
  cardTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  storeName: { flex: 1, fontSize: 15, color: '#1E3932', marginRight: 8 },
  badge: { fontSize: 11, color: '#00A859' },
  interest: { fontSize: 13, color: '#00A859', marginTop: 6 },
  address: { fontSize: 12, color: '#888', marginTop: 4 },
});
