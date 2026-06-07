import { apiClient } from './apiClient';

export const authApi = {
  // 로그인
  login: (email, password) =>
    apiClient.post('/auth/login', { email, password }),

  // 로그아웃
  logout: (refreshToken) => apiClient.post('/auth/logout', { refreshToken }),

  // 회원가입
  signUpOwner: (signUpData) => apiClient.post('/auth/signup/owner', signUpData),

  // 이메일 인증 코드 발송
  sendEmailVerification: (email) =>
    apiClient.post('/auth/email/send-verification', { email }),

  // 이메일 인증 코드 확인
  verifyEmailCode: (email, code) =>
    apiClient.post('/auth/email/verify', { email, code }),

  // 비밀번호 찾기 메일 발송
  requestPasswordReset: (email) =>
    apiClient.post('/auth/password/reset-request', { email }),

  // 비밀번호 찾기 인증 코드 확인
  verifyPasswordResetCode: (email, code) =>
    apiClient.post('/auth/password/verify', { email, code }),

  // 비밀번호 최종 재설정
  resetPassword: (resetData) =>
    apiClient.post('/auth/password/reset', resetData),
};
