import axios from 'axios';

// 공통 인스턴스 생성 (서버 주소 설정)
const axiosInstance = axios.create({
  baseURL: 'http://localhost:8080',
});

// 토큰 주입 인터셉터 설정 (매번 headers 적을 필요 없게 자동화)
axiosInstance.interceptors.request.use((config) => {
  const token =
    localStorage.getItem('accessToken') ||
    sessionStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 상점 도메인 관련 API 함수 모음집 (export)
export const storeApi = {
  // 내 상점 정보 조회 / 수정
  getStoreInfo: () =>
    axiosInstance.get('/owner/stores/me').then((res) => res.data),
  updateStoreInfo: (storeData) =>
    axiosInstance.patch('/owner/stores/me', storeData).then((res) => res.data),

  // 영업 상태 변경
  updateStatus: (status) =>
    axiosInstance
      .patch('/owner/stores/me/status', { status })
      .then((res) => res.data),

  // 공지사항 목록 조회 / 등록 / 수정 / 삭제
  getNotices: () =>
    axiosInstance.get('/owner/stores/me/notices').then((res) => res.data),
  addNotice: (noticeData) =>
    axiosInstance
      .post('/owner/stores/me/notices', noticeData)
      .then((res) => res.data),
  updateNotice: (noticeId, noticeData) =>
    axiosInstance
      .patch(`/owner/stores/me/notices/${noticeId}`, noticeData)
      .then((res) => res.data),
  deleteNotice: (noticeId) =>
    axiosInstance
      .delete(`/owner/stores/me/notices/${noticeId}`)
      .then((res) => res.data),

  // 상점 이미지 목록 조회 / 등록 / 삭제 / 대표 설정
  getImages: () =>
    axiosInstance.get('/owner/stores/me/images').then((res) => res.data),
  uploadImages: (imageData) =>
    axiosInstance
      .post('/owner/stores/me/images', imageData)
      .then((res) => res.data),
  deleteImage: (imageId) =>
    axiosInstance
      .delete(`/owner/stores/me/images/${imageId}`)
      .then((res) => res.data),
  setThumbnail: (imageId) =>
    axiosInstance
      .patch(`/owner/stores/me/images/${imageId}/thumbnail`)
      .then((res) => res.data),
};
