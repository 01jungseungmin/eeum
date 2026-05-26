import React from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';

interface HomeHeaderProps {
  primaryRegionName: string;
  onOpenModal: () => void;
  activeTab: 'shop' | 'used';
  setActiveTab: (tab: 'shop' | 'used') => void;
  onSearch: () => void;
}

export default function HomeHeader({
  primaryRegionName,
  onOpenModal,
  activeTab,
  setActiveTab,
  onSearch
}: HomeHeaderProps) {
  return (
    <View style={styles.headerContainer}>
      <View style={styles.headerTop}>
        <TouchableOpacity style={styles.locationSelector} onPress={onOpenModal}>
          <Ionicons name="location-sharp" size={18} color="#00A859" />
          <Text style={styles.headerLocationText}>{primaryRegionName}</Text>
          <Ionicons name="chevron-down" size={16} color="#333" />
        </TouchableOpacity>
        <View style={styles.headerIcons}>
          <TouchableOpacity style={{ marginRight: 15 }}>
            <Ionicons name="notifications-outline" size={24} color="#333" />
          </TouchableOpacity>
          <TouchableOpacity>
            <Ionicons name="cart-outline" size={24} color="#333" />
          </TouchableOpacity>
        </View>
      </View>

      <View style={styles.toggleContainer}>
        <TouchableOpacity 
          style={[styles.toggleBtn, activeTab === 'shop' && styles.toggleBtnActive]} 
          onPress={() => setActiveTab('shop')}
        >
          <Text style={[styles.toggleText, activeTab === 'shop' && styles.toggleTextActive]}>전체상점</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.toggleBtn, activeTab === 'used' && styles.toggleBtnActive]} 
          onPress={() => setActiveTab('used')}
        >
          <Text style={[styles.toggleText, activeTab === 'used' && styles.toggleTextActive]}>중고거래</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.searchBar} onPress={onSearch}>
        <Text style={styles.searchText}>검색어를 입력해주세요</Text>
        <Ionicons name="search" size={20} color="#00A859" />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  headerContainer: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 15, backgroundColor: '#fff' },
  headerTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  locationSelector: { flexDirection: 'row', alignItems: 'center' },
  headerLocationText: { fontSize: 16, fontWeight: 'bold', color: '#333', marginHorizontal: 5 },
  headerIcons: { flexDirection: 'row' },
  toggleContainer: { flexDirection: 'row', backgroundColor: '#F5F5F5', borderRadius: 25, padding: 4, marginBottom: 15, width: 200 },
  toggleBtn: { flex: 1, paddingVertical: 8, alignItems: 'center', borderRadius: 20 },
  toggleBtnActive: { backgroundColor: '#00A859' },
  toggleText: { fontSize: 14, color: '#888', fontWeight: '600' },
  toggleTextActive: { color: '#fff' },
  searchBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#F5F5F5', paddingHorizontal: 15, paddingVertical: 12, borderRadius: 8 },
  searchText: { color: '#999', fontSize: 14 },
});