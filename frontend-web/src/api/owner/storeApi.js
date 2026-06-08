import axios from 'axios';
import { apiClient } from '../apiClient';

// 상점 도메인 관련 API 함수 모음집 (export)
export const storeApi = {
  // 내 상점 정보 조회 / 수정
  getStoreInfo: () => apiClient.get('/owner/stores/me').then((res) => res.data),
  updateStoreInfo: (storeData) =>
    apiClient.patch('/owner/stores/me', storeData).then((res) => res.data),

  // 영업시간 조회 / 수정
  getBusinessHours: () => apiClient.get('/owner/stores/me/business-hours'),
  updateBusinessHours: (data) =>
    apiClient.put('/owner/stores/me/business-hours', data),

  // 영업 상태 변경
  updateStatus: (status) =>
    apiClient
      .patch('/owner/stores/me/status', { status })
      .then((res) => res.data),

  // 공지사항 목록 조회 / 등록 / 수정 / 삭제
  getNotices: () =>
    apiClient.get('/owner/stores/me/notices').then((res) => res.data),
  addNotice: (noticeData) =>
    apiClient
      .post('/owner/stores/me/notices', noticeData)
      .then((res) => res.data),
  updateNotice: (noticeId, noticeData) =>
    apiClient
      .patch(`/owner/stores/me/notices/${noticeId}`, noticeData)
      .then((res) => res.data),
  deleteNotice: (noticeId) =>
    apiClient
      .delete(`/owner/stores/me/notices/${noticeId}`)
      .then((res) => res.data),

  // 상점 이미지 목록 조회 / 등록 / 삭제 / 대표 설정
  getImages: () =>
    apiClient.get('/owner/stores/me/images').then((res) => res.data),
  uploadImages: (imageData) =>
    apiClient
      .post('/owner/stores/me/images', imageData)
      .then((res) => res.data),
  deleteImage: (imageId) =>
    apiClient
      .delete(`/owner/stores/me/images/${imageId}`)
      .then((res) => res.data),
  setThumbnail: (imageId) =>
    apiClient
      .patch(`/owner/stores/me/images/${imageId}/thumbnail`)
      .then((res) => res.data),
};
